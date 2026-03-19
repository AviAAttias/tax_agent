package com.abco.taxassessment.domain.agent.domain.service;

import com.abco.taxassessment.domain.agent.domain.entity.AgentJobEntity;
import com.abco.taxassessment.domain.agent.repository.AgentJobRepository;
import com.abco.taxassessment.domain.agent.repository.ProcessedEventRepository;
import com.abco.taxassessment.domain.agent.domain.entity.ProcessedEventEntity;
import com.abco.taxassessment.event.domain.BaseAgentEvent;
import com.abco.taxassessment.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Base class for all agent services.
 *
 * Provides:
 * 1. Idempotency check — deduplicates on eventId+consumerGroup (§9.5)
 * 2. TenantContext establishment from event header
 * 3. AgentJob lifecycle management (start → complete → fail → DLQ)
 * 4. Metric recording per agent
 *
 * Each concrete agent implements processEvent() with single-responsibility logic.
 */
public abstract class AbstractAgentService<T extends BaseAgentEvent> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final AgentJobRepository agentJobRepository;
    protected final ProcessedEventRepository processedEventRepository;
    protected final KafkaTemplate<String, Object> kafkaTemplate;
    protected final MeterRegistry meterRegistry;

    protected AbstractAgentService(AgentJobRepository agentJobRepository,
                                    ProcessedEventRepository processedEventRepository,
                                    KafkaTemplate<String, Object> kafkaTemplate,
                                    MeterRegistry meterRegistry) {
        this.agentJobRepository = agentJobRepository;
        this.processedEventRepository = processedEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Entry point called by Kafka consumer.
     * Establishes tenant context, checks idempotency, delegates to processEvent().
     */
    @Transactional
    public final void handle(T event, String consumerGroup) {
        String agentName = getAgentName().name();

        // 1. Idempotency: if already processed, acknowledge and return (§9.5)
        if (processedEventRepository.existsByEventIdAndConsumerGroup(
                event.getEventId(), consumerGroup)) {
            log.info("Duplicate event {} for agent {} — skipping", event.getEventId(), agentName);
            meterRegistry.counter("agent.events.duplicate", "agent", agentName).increment();
            return;
        }

        // 2. Re-establish TenantContext from event (§9.5)
        TenantContext.set(event.getTenantId());

        try {
            // 3. Find or create AgentJob
            AgentJobEntity job = findOrCreateJob(event);
            job.start(event.getEventId());
            agentJobRepository.save(job);

            // 4. Process the event
            String outputEventId = processEvent(event);

            // 5. Complete the job
            job.complete(outputEventId);
            agentJobRepository.save(job);

            // 6. Record idempotency
            processedEventRepository.save(
                ProcessedEventEntity.of(event.getEventId(), consumerGroup,
                                        event.getTenantId().toString()));

            meterRegistry.counter("agent.events.processed",
                    "agent", agentName, "status", "success").increment();

            log.info("Agent {} processed event {} for tenant {}",
                     agentName, event.getEventId(), event.getTenantId());

        } catch (Exception e) {
            log.error("Agent {} failed processing event {} for tenant {}: {}",
                      agentName, event.getEventId(), event.getTenantId(), e.getMessage(), e);

            agentJobRepository.findByTenantIdAndStatementIdAndAgentName(
                    event.getTenantId(), getStatementId(event), getAgentName())
                .ifPresent(job -> {
                    job.fail(e.getMessage());
                    agentJobRepository.save(job);
                });

            meterRegistry.counter("agent.events.failed",
                    "agent", agentName, "error", e.getClass().getSimpleName()).increment();

            // Re-throw to let Kafka listener handle retry/DLQ routing
            throw new RuntimeException("Agent processing failed: " + e.getMessage(), e);
        } finally {
            TenantContext.clear();
        }
    }

    /** Implement the agent-specific processing logic. Returns the output eventId. */
    protected abstract String processEvent(T event);

    protected abstract AgentJobEntity.AgentName getAgentName();

    protected abstract UUID getStatementId(T event);

    private AgentJobEntity findOrCreateJob(T event) {
        return agentJobRepository.findByTenantIdAndStatementIdAndAgentName(
                event.getTenantId(), getStatementId(event), getAgentName())
            .orElseGet(() -> AgentJobEntity.builder()
                .tenantId(event.getTenantId())
                .statementId(getStatementId(event))
                .agentName(getAgentName())
                .status(AgentJobEntity.AgentJobStatus.PENDING)
                .attemptCount(0)
                .build());
    }
}
