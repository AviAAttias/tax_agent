package com.abco.taxassessment.domain.agent.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Idempotency store for Kafka consumer deduplication (§9.5).
 *
 * Each agent consumer records the eventId of every successfully processed event.
 * Before processing, the consumer checks this table. If the eventId is present,
 * the message is acknowledged without re-processing (idempotent consumer).
 *
 * consumer_group is included so that different consumers can process the same event independently.
 */
@Entity
@Table(name = "processed_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEventEntity {

    /** Composite: eventId + consumerGroup — enforced by DB unique constraint */
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(name = "consumer_group", nullable = false, updatable = false, length = 255)
    private String consumerGroup;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    public static ProcessedEventEntity of(String eventId, String consumerGroup, String tenantId) {
        return ProcessedEventEntity.builder()
                .id(eventId + ":" + consumerGroup)
                .eventId(eventId)
                .consumerGroup(consumerGroup)
                .tenantId(tenantId)
                .processedAt(Instant.now())
                .build();
    }
}
