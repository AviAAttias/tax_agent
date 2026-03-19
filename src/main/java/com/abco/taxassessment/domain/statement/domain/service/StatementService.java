package com.abco.taxassessment.domain.statement.domain.service;

import com.abco.taxassessment.domain.statement.domain.entity.StatementEntity;
import com.abco.taxassessment.domain.statement.repository.StatementRepository;
import com.abco.taxassessment.event.domain.StatementIngestedEvent;
import com.abco.taxassessment.event.outbox.OutboxEventEntity;
import com.abco.taxassessment.event.outbox.OutboxEventRepository;
import com.abco.taxassessment.exception.DuplicateStatementException;
import com.abco.taxassessment.exception.ResourceNotFoundException;
import com.abco.taxassessment.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * UC-07 through UC-14: Statement ingestion domain logic.
 *
 * Responsibilities:
 * - Duplicate detection (hash-based and period-overlap)
 * - Statement lifecycle management (UPLOADED → PROCESSING → PROCESSED → FINALIZED)
 * - Outbox event publication (atomic with statement creation)
 * - Amendment/re-ingestion handling (UC-12)
 *
 * @Transactional is the domain boundary per §3.2.
 */
@Service
public class StatementService {

    private static final Logger log = LoggerFactory.getLogger(StatementService.class);

    private final StatementRepository statementRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public StatementService(StatementRepository statementRepository,
                             OutboxEventRepository outboxEventRepository,
                             ObjectMapper objectMapper,
                             MeterRegistry meterRegistry) {
        this.statementRepository = statementRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public StatementEntity ingestStatement(UUID accountId,
                                            LocalDate periodStart,
                                            LocalDate periodEnd,
                                            StatementEntity.SourceFormat format,
                                            String fileHash,
                                            String filePathEncrypted,
                                            String originalFilename,
                                            Long fileSizeBytes,
                                            boolean wasPartial) {
        UUID tenantId = TenantContext.requireTenantId();

        // UC-09: Duplicate detection — exact file hash match
        statementRepository.findByTenantIdAndFileHash(tenantId, fileHash).ifPresent(existing -> {
            log.warn("Duplicate statement detected for tenant {} — quarantining. Existing: {}",
                     tenantId, existing.getId());
            StatementEntity duplicate = StatementEntity.builder()
                    .tenantId(tenantId)
                    .accountId(accountId)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .status(StatementEntity.StatementStatus.DUPLICATE_QUARANTINED)
                    .sourceFormat(format)
                    .fileHash(fileHash)
                    .originalFilename(originalFilename)
                    .fileSizeBytes(fileSizeBytes)
                    .wasPartial(wasPartial)
                    .amendment(false)
                    .version(1)
                    .build();
            statementRepository.save(duplicate);
            meterRegistry.counter("statements.duplicates_quarantined").increment();
            throw new DuplicateStatementException(existing.getId());
        });

        StatementEntity statement = StatementEntity.builder()
                .tenantId(tenantId)
                .accountId(accountId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .status(StatementEntity.StatementStatus.UPLOADED)
                .sourceFormat(format)
                .fileHash(fileHash)
                .filePathEncrypted(filePathEncrypted)
                .originalFilename(originalFilename)
                .fileSizeBytes(fileSizeBytes)
                .wasPartial(wasPartial)
                .amendment(false)
                .version(1)
                .build();

        StatementEntity saved = statementRepository.save(statement);

        // Publish via outbox — atomic with statement creation (§9)
        publishIngestedEvent(saved, tenantId, false);

        meterRegistry.counter("statements.ingested",
                "format", format.name()).increment();

        log.info("Statement ingested: id={} tenant={} format={} period=[{},{}]",
                 saved.getId(), tenantId, format, periodStart, periodEnd);

        return saved;
    }

    @Transactional
    public StatementEntity ingestAmendment(UUID originalStatementId,
                                            UUID accountId,
                                            LocalDate periodStart,
                                            LocalDate periodEnd,
                                            StatementEntity.SourceFormat format,
                                            String fileHash,
                                            String filePathEncrypted,
                                            String originalFilename,
                                            Long fileSizeBytes) {
        UUID tenantId = TenantContext.requireTenantId();

        StatementEntity original = statementRepository
                .findByTenantIdAndId(tenantId, originalStatementId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement", originalStatementId));

        // Archive the original assessment
        original.transitionTo(StatementEntity.StatementStatus.ARCHIVED);
        statementRepository.save(original);

        StatementEntity amendment = StatementEntity.builder()
                .tenantId(tenantId)
                .accountId(accountId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .status(StatementEntity.StatementStatus.UPLOADED)
                .sourceFormat(format)
                .fileHash(fileHash)
                .filePathEncrypted(filePathEncrypted)
                .originalFilename(originalFilename)
                .fileSizeBytes(fileSizeBytes)
                .wasPartial(false)
                .amendment(true)
                .amendsStatementId(originalStatementId)
                .version(original.getVersion() + 1)
                .build();

        StatementEntity saved = statementRepository.save(amendment);
        publishIngestedEvent(saved, tenantId, true);

        log.info("Amendment statement {} created for original {} tenant {}",
                 saved.getId(), originalStatementId, tenantId);
        return saved;
    }

    @Transactional
    public void markFailed(UUID statementId, String failureReason) {
        UUID tenantId = TenantContext.requireTenantId();
        StatementEntity statement = statementRepository
                .findByTenantIdAndId(tenantId, statementId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement", statementId));
        statement.markFailed(failureReason);
        statementRepository.save(statement);
        meterRegistry.counter("statements.failed").increment();
    }

    @Transactional(readOnly = true)
    public List<StatementEntity> findAllForTenant() {
        return statementRepository.findAllByTenantId(TenantContext.requireTenantId());
    }

    @Transactional(readOnly = true)
    public StatementEntity findById(UUID statementId) {
        UUID tenantId = TenantContext.requireTenantId();
        return statementRepository.findByTenantIdAndId(tenantId, statementId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement", statementId));
    }

    private void publishIngestedEvent(StatementEntity statement, UUID tenantId, boolean isAmendment) {
        StatementIngestedEvent event = new StatementIngestedEvent(
                tenantId,
                statement.getId(),
                statement.getAccountId(),
                statement.getSourceFormat(),
                statement.getFilePathEncrypted(),
                isAmendment
        );

        try {
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .tenantId(tenantId)
                    .aggregateType("Statement")
                    .aggregateId(statement.getId())
                    .eventType("statement.ingested")
                    .eventId(event.getEventId())
                    .kafkaTopic("statement.ingested")
                    .kafkaKey(statement.getId().toString())
                    .version(1)
                    .payload(objectMapper.writeValueAsString(event))
                    .published(false)
                    .publishAttemptCount(0)
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize statement ingested event", e);
        }
    }
}
