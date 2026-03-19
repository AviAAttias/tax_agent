package com.abco.taxassessment.event.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox pattern event (§9).
 *
 * Written atomically with the domain aggregate in the same transaction.
 * The OutboxPoller reads unpublished events and publishes them to Kafka,
 * then marks them as published. This eliminates the dual-write problem.
 *
 * event_id is an idempotency key — consumers deduplicate on this value.
 */
@Entity
@Table(name = "outbox_events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "aggregate_type", nullable = false, length = 255)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    /** Idempotency key for consumers — globally unique per event occurrence */
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "kafka_topic", nullable = false, length = 255)
    private String kafkaTopic;

    @Column(name = "kafka_key", length = 255)
    private String kafkaKey;

    @CreatedDate
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "publish_attempt_count", nullable = false)
    private int publishAttemptCount;

    @Column(name = "last_publish_error", columnDefinition = "text")
    private String lastPublishError;

    public void markPublished() {
        this.published = true;
        this.publishedAt = Instant.now();
    }

    public void recordPublishFailure(String error) {
        this.publishAttemptCount++;
        this.lastPublishError = error;
    }

    public void incrementAttempt() {
        this.publishAttemptCount++;
    }
}
