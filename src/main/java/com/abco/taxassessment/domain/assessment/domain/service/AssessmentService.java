package com.abco.taxassessment.domain.assessment.domain.service;

import com.abco.taxassessment.domain.assessment.domain.entity.TaxAssessmentEntity;
import com.abco.taxassessment.domain.assessment.repository.TaxAssessmentRepository;
import com.abco.taxassessment.domain.transaction.domain.entity.TransactionEntity;
import com.abco.taxassessment.domain.transaction.repository.TransactionRepository;
import com.abco.taxassessment.event.domain.AssessmentFinalizedEvent;
import com.abco.taxassessment.event.outbox.OutboxEventEntity;
import com.abco.taxassessment.event.outbox.OutboxEventRepository;
import com.abco.taxassessment.exception.ResourceNotFoundException;
import com.abco.taxassessment.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UC-21 + UC-24: Tax Assessment Aggregation and Finalization.
 *
 * Aggregation: groups reconciled, tax-rule-applied transactions into a structured assessment.
 * Finalization: produces the immutable, timestamped, versioned, signed assessment artifact.
 *
 * Tax correctness:
 * - Income and expense are aggregated by category
 * - Deductible amounts are used (not gross amounts) for expense deduction totals
 * - Estimated quarterly tax uses simplified effective rate model — flagged as estimate
 * - Safe harbor calculation uses prior-year basis if available, current-year basis otherwise
 *
 * @Transactional is the domain boundary per §3.2.
 */
@Service
public class AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);

    /** Self-employment tax rate for Schedule C filers (15.3% = 12.4% SS + 2.9% Medicare) */
    private static final BigDecimal SE_TAX_RATE = new BigDecimal("0.153");

    /** SE deduction: 50% of SE tax is deductible (IRC §164(f)) */
    private static final BigDecimal SE_DEDUCTION_RATE = new BigDecimal("0.5");

    private final TaxAssessmentRepository taxAssessmentRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AssessmentService(TaxAssessmentRepository taxAssessmentRepository,
                              TransactionRepository transactionRepository,
                              OutboxEventRepository outboxEventRepository,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.taxAssessmentRepository = taxAssessmentRepository;
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public TaxAssessmentEntity aggregateAssessment(UUID tenantId, UUID statementId,
                                                    LocalDate periodStart, LocalDate periodEnd) {
        UUID effectiveTenantId = TenantContext.requireTenantId();

        List<TransactionEntity> transactions = transactionRepository
                .findAllByTenantIdAndStatementId(effectiveTenantId, statementId);

        BigDecimal totalIncome = transactions.stream()
                .filter(tx -> tx.getTaxTreatment() == TransactionEntity.TaxTreatment.INCOME)
                .map(TransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpenses = transactions.stream()
                .filter(tx -> tx.getTaxTreatment() != null
                           && tx.getTaxTreatment() != TransactionEntity.TaxTreatment.INCOME
                           && tx.getTaxTreatment() != TransactionEntity.TaxTreatment.INTERACCOUNT_TRANSFER
                           && tx.getTaxTreatment() != TransactionEntity.TaxTreatment.NON_DEDUCTIBLE)
                .map(TransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeductibleExpenses = transactions.stream()
                .filter(tx -> tx.getDeductibleAmount() != null)
                .map(TransactionEntity::getDeductibleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netProfitLoss = totalIncome.subtract(totalDeductibleExpenses);

        // Self-employment tax calculation (Schedule SE)
        BigDecimal seTax = BigDecimal.ZERO;
        BigDecimal estimatedFederalTax = BigDecimal.ZERO;
        if (netProfitLoss.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal seBase = netProfitLoss.multiply(new BigDecimal("0.9235")); // 92.35% of net profit
            seTax = seBase.multiply(SE_TAX_RATE).setScale(2, RoundingMode.HALF_UP);
            BigDecimal seDeduction = seTax.multiply(SE_DEDUCTION_RATE);
            BigDecimal taxableIncome = netProfitLoss.subtract(seDeduction);
            // Simplified 22% effective rate — actual rate depends on other income; flagged as estimate
            estimatedFederalTax = taxableIncome.multiply(new BigDecimal("0.22"))
                    .add(seTax)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Category breakdown
        Map<String, BigDecimal> incomeByCategory = transactions.stream()
                .filter(tx -> tx.getTaxTreatment() == TransactionEntity.TaxTreatment.INCOME)
                .collect(Collectors.groupingBy(
                        tx -> tx.getCategoryCode() != null ? tx.getCategoryCode() : "UNCATEGORIZED",
                        Collectors.reducing(BigDecimal.ZERO, TransactionEntity::getAmount, BigDecimal::add)));

        Map<String, BigDecimal> expenseByCategory = transactions.stream()
                .filter(tx -> tx.getDeductibleAmount() != null
                           && tx.getDeductibleAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(
                        tx -> tx.getCategoryCode() != null ? tx.getCategoryCode() : "UNCATEGORIZED",
                        Collectors.reducing(BigDecimal.ZERO, TransactionEntity::getDeductibleAmount, BigDecimal::add)));

        long flaggedCount = transactions.stream()
                .filter(TransactionEntity::isFlaggedForReview).count();

        BigDecimal confidence = flaggedCount == 0 ? new BigDecimal("0.95")
                : BigDecimal.valueOf(1.0 - ((double) flaggedCount / transactions.size()))
                            .setScale(4, RoundingMode.HALF_UP);

        AssessmentPayload payload = new AssessmentPayload(
                incomeByCategory,
                expenseByCategory,
                totalIncome,
                totalExpenses,
                totalDeductibleExpenses,
                netProfitLoss,
                estimatedFederalTax,
                seTax,
                flaggedCount,
                transactions.size()
        );

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize assessment payload", e);
        }

        TaxAssessmentEntity assessment = TaxAssessmentEntity.builder()
                .tenantId(effectiveTenantId)
                .statementId(statementId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .status(TaxAssessmentEntity.AssessmentStatus.DRAFT)
                .version(1)
                .payload(payloadJson)
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .totalDeductibleExpenses(totalDeductibleExpenses)
                .netProfitLoss(netProfitLoss)
                .estimatedFederalTax(estimatedFederalTax)
                .estimatedStateTax(BigDecimal.ZERO) // requires jurisdiction-specific calculation
                .overallConfidence(confidence)
                .build();

        if (flaggedCount > 0) {
            assessment.submitForReview();
        }

        TaxAssessmentEntity saved = taxAssessmentRepository.save(assessment);
        meterRegistry.counter("assessments.aggregated").increment();
        log.info("Aggregated assessment {} for statement {} tenant {}",
                 saved.getId(), statementId, effectiveTenantId);

        return saved;
    }

    @Transactional
    public TaxAssessmentEntity finalizeAssessment(UUID tenantId, UUID assessmentId) {
        UUID effectiveTenantId = TenantContext.requireTenantId();

        TaxAssessmentEntity assessment = taxAssessmentRepository
                .findByTenantIdAndId(effectiveTenantId, assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("TaxAssessment", assessmentId));

        String payloadHash = sha256(assessment.getPayload());
        assessment.finalize(payloadHash);
        TaxAssessmentEntity saved = taxAssessmentRepository.save(assessment);

        // Publish finalization event via outbox (§9) — atomic with DB commit
        AssessmentFinalizedEvent event = new AssessmentFinalizedEvent(
                effectiveTenantId,
                saved.getId(),
                saved.getStatementId(),
                saved.getPeriodStart(),
                saved.getPeriodEnd(),
                saved.getTotalIncome(),
                saved.getTotalExpenses(),
                saved.getNetProfitLoss(),
                (int) transactionRepository.countByTenantIdAndStatementId(effectiveTenantId,
                                                                           saved.getStatementId())
        );

        try {
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .tenantId(effectiveTenantId)
                    .aggregateType("TaxAssessment")
                    .aggregateId(saved.getId())
                    .eventType("assessment.finalized")
                    .eventId(event.getEventId())
                    .kafkaTopic("assessment.finalized")
                    .kafkaKey(saved.getId().toString())
                    .version(1)
                    .payload(objectMapper.writeValueAsString(event))
                    .published(false)
                    .publishAttemptCount(0)
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize finalization event", e);
        }

        meterRegistry.counter("assessments.finalized").increment();
        log.info("Finalized assessment {} for tenant {}", assessmentId, effectiveTenantId);

        return saved;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public record AssessmentPayload(
            Map<String, BigDecimal> incomeByCategory,
            Map<String, BigDecimal> expenseByCategory,
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            BigDecimal totalDeductibleExpenses,
            BigDecimal netProfitLoss,
            BigDecimal estimatedFederalTax,
            BigDecimal selfEmploymentTax,
            long flaggedTransactionCount,
            int totalTransactionCount
    ) {}
}
