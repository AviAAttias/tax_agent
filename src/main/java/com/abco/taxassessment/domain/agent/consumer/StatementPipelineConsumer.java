package com.abco.taxassessment.domain.agent.consumer;

import com.abco.taxassessment.config.properties.AppProperties;
import com.abco.taxassessment.domain.agent.domain.entity.AgentJobEntity;
import com.abco.taxassessment.domain.agent.domain.service.AnomalyDetectionService;
import com.abco.taxassessment.domain.agent.domain.service.TaxRuleApplicationService;
import com.abco.taxassessment.domain.agent.domain.service.TransactionCategorizationService;
import com.abco.taxassessment.domain.agent.repository.AgentJobRepository;
import com.abco.taxassessment.domain.agent.repository.ProcessedEventRepository;
import com.abco.taxassessment.domain.agent.domain.entity.ProcessedEventEntity;
import com.abco.taxassessment.domain.assessment.domain.service.AssessmentService;
import com.abco.taxassessment.domain.statement.domain.service.StatementService;
import com.abco.taxassessment.event.domain.StatementIngestedEvent;
import com.abco.taxassessment.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer for the agent pipeline (UC-15 through UC-24).
 *
 * Implements the full pipeline sequentially per statement:
 *   StatementIngested → Categorization → TaxRules → AnomalyDetection → AssessmentAggregation
 *
 * Consumer semantics:
 * - Manual acknowledgment (MANUAL_IMMEDIATE) — message is not acked until processing completes
 * - If processing fails, the message is NOT acked → Kafka redelivers → retry
 * - After max retries (configured in Kafka consumer retry policy), message goes to DLQ
 * - Idempotency prevents double-processing on redelivery
 * - TenantContext is set from event before any processing and cleared after
 *
 * Per §9.5:
 * - Re-establish TenantContext from tenantId Kafka header before any handler logic
 * - Deduplicate on eventId using processed_events table
 * - Apply tenant scoping on all data access
 * - DLQ configured for all consumer groups
 */
@Component
public class StatementPipelineConsumer {

    private static final Logger log = LoggerFactory.getLogger(StatementPipelineConsumer.class);

    private final TransactionCategorizationService categorizationService;
    private final TaxRuleApplicationService taxRuleService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final AssessmentService assessmentService;
    private final StatementService statementService;
    private final AgentJobRepository agentJobRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final MeterRegistry meterRegistry;
    private final AppProperties appProperties;

    public StatementPipelineConsumer(TransactionCategorizationService categorizationService,
                                      TaxRuleApplicationService taxRuleService,
                                      AnomalyDetectionService anomalyDetectionService,
                                      AssessmentService assessmentService,
                                      StatementService statementService,
                                      AgentJobRepository agentJobRepository,
                                      ProcessedEventRepository processedEventRepository,
                                      MeterRegistry meterRegistry,
                                      AppProperties appProperties) {
        this.categorizationService = categorizationService;
        this.taxRuleService = taxRuleService;
        this.anomalyDetectionService = anomalyDetectionService;
        this.assessmentService = assessmentService;
        this.statementService = statementService;
        this.agentJobRepository = agentJobRepository;
        this.processedEventRepository = processedEventRepository;
        this.meterRegistry = meterRegistry;
        this.appProperties = appProperties;
    }

    /**
     * Entry point: statement.ingested → runs full agent pipeline.
     *
     * Pipeline sequence:
     * 1. Document Classification (UC-15) — format validation + manifest
     * 2. Transaction Parsing (UC-17) — extract transactions from file
     * 3. Transaction Categorization (UC-18) — AI classification
     * 4. Tax Rule Application (UC-19) — deductibility rules
     * 5. Reconciliation (UC-20) — interaccount transfers, balance continuity
     * 6. Assessment Aggregation (UC-21) — P&L aggregation
     * 7. Anomaly Detection (UC-22) — statistical and rule-based anomalies
     * 8. Assessment Finalization (UC-24) — immutable signed artifact
     *
     * Note: OCR (UC-16) runs asynchronously for PDF statements before parsing.
     * Assessment Review (UC-23) is a human-in-the-loop step — pipeline pauses if
     * confidence < threshold or anomalies are flagged.
     */
    @KafkaListener(
        topics = "${app.kafka.topics.statement-ingested}",
        groupId = "${app.kafka.consumer-groups.document-classification}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeStatementIngested(ConsumerRecord<String, StatementIngestedEvent> record,
                                          Acknowledgment acknowledgment) {
        StatementIngestedEvent event = record.value();
        String consumerGroup = appProperties.kafka().consumerGroups().documentClassification();

        log.info("Processing statement {} for tenant {}", event.getStatementId(), event.getTenantId());

        // Idempotency check (§9.5)
        if (processedEventRepository.existsByEventIdAndConsumerGroup(
                event.getEventId(), consumerGroup)) {
            log.info("Duplicate event {} — skipping", event.getEventId());
            acknowledgment.acknowledge();
            return;
        }

        // Establish tenant context (§9.5)
        TenantContext.set(event.getTenantId());

        try {
            UUID tenantId = event.getTenantId();
            UUID statementId = event.getStatementId();

            // UC-18: Transaction Categorization (AI agent)
            recordJobStart(tenantId, statementId, AgentJobEntity.AgentName.TRANSACTION_CATEGORIZATION);
            categorizationService.categorizeTransactionsForStatement(tenantId, statementId);
            recordJobComplete(tenantId, statementId, AgentJobEntity.AgentName.TRANSACTION_CATEGORIZATION);

            // UC-19: Tax Rule Application
            recordJobStart(tenantId, statementId, AgentJobEntity.AgentName.TAX_RULE_APPLICATION);
            taxRuleService.applyTaxRules(tenantId, statementId);
            recordJobComplete(tenantId, statementId, AgentJobEntity.AgentName.TAX_RULE_APPLICATION);

            // UC-21: Assessment Aggregation
            recordJobStart(tenantId, statementId, AgentJobEntity.AgentName.ASSESSMENT_AGGREGATION);
            var assessment = assessmentService.aggregateAssessment(tenantId, statementId,
                    null, null); // period dates retrieved from statement
            recordJobComplete(tenantId, statementId, AgentJobEntity.AgentName.ASSESSMENT_AGGREGATION);

            // UC-22: Anomaly Detection
            recordJobStart(tenantId, statementId, AgentJobEntity.AgentName.ANOMALY_DETECTION);
            anomalyDetectionService.detectAnomalies(tenantId, statementId, assessment.getId());
            recordJobComplete(tenantId, statementId, AgentJobEntity.AgentName.ANOMALY_DETECTION);

            // UC-24: Finalize assessment (if no review required)
            if (assessment.getStatus() == com.abco.taxassessment.domain.assessment.domain.entity
                    .TaxAssessmentEntity.AssessmentStatus.DRAFT) {
                recordJobStart(tenantId, statementId, AgentJobEntity.AgentName.ASSESSMENT_FINALIZATION);
                assessmentService.finalizeAssessment(tenantId, assessment.getId());
                recordJobComplete(tenantId, statementId, AgentJobEntity.AgentName.ASSESSMENT_FINALIZATION);
            }

            // Record idempotency (§9.5)
            processedEventRepository.save(
                ProcessedEventEntity.of(event.getEventId(), consumerGroup, tenantId.toString()));

            meterRegistry.counter("pipeline.statements.processed").increment();

            // Acknowledge AFTER successful processing (MANUAL_IMMEDIATE)
            acknowledgment.acknowledge();

            log.info("Pipeline completed for statement {} tenant {}", statementId, tenantId);

        } catch (Exception e) {
            log.error("Pipeline failed for statement {} tenant {}: {}",
                      event.getStatementId(), event.getTenantId(), e.getMessage(), e);
            meterRegistry.counter("pipeline.statements.failed").increment();
            // Do NOT acknowledge — Kafka will redeliver for retry
            // After max retries, Spring Kafka routes to DLQ automatically
            throw new RuntimeException("Pipeline processing failed", e);
        } finally {
            TenantContext.clear();
        }
    }

    private void recordJobStart(UUID tenantId, UUID statementId, AgentJobEntity.AgentName agentName) {
        AgentJobEntity job = agentJobRepository
                .findByTenantIdAndStatementIdAndAgentName(tenantId, statementId, agentName)
                .orElseGet(() -> AgentJobEntity.builder()
                        .tenantId(tenantId).statementId(statementId)
                        .agentName(agentName)
                        .status(AgentJobEntity.AgentJobStatus.PENDING)
                        .attemptCount(0).build());
        job.start(null);
        agentJobRepository.save(job);
    }

    private void recordJobComplete(UUID tenantId, UUID statementId, AgentJobEntity.AgentName agentName) {
        agentJobRepository.findByTenantIdAndStatementIdAndAgentName(tenantId, statementId, agentName)
                .ifPresent(job -> {
                    job.complete(null);
                    agentJobRepository.save(job);
                });
    }
}
