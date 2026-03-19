package com.abco.taxassessment.domain.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.type.descriptor.java.UUIDJavaType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification event record — tracks every notification attempt with delivery status.
 *
 * At-least-once delivery is guaranteed via idempotency on notification_event.id (§Architecture).
 * Retries (up to 3) update status on this record before marking as FAILED.
 * Provider message ID is stored for delivery tracking and reconciliation.
 */
@Entity
@Table(name = "notification_events")
@EntityListeners(AuditingEntityListener.class)
@FilterDef(
    name = "tenantFilter",
    parameters = @ParamDef(name = "tenantId", type = UUIDJavaType.class)
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    private NotificationEventType eventType;

    @Column(name = "channel", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    /** Notification payload as JSON (subject, body, template data) */
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** For digest grouping — null for immediate notifications */
    @Column(name = "digest_group_id")
    private UUID digestGroupId;

    @Column(name = "idempotency_key", nullable = false, length = 255, unique = true)
    private String idempotencyKey;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum NotificationEventType {
        PROCESSING_COMPLETE,
        ANOMALY_ALERT,
        REVIEW_REQUIRED,
        QUARTERLY_TAX_REMINDER,
        INGESTION_FAILURE,
        REGULATORY_FLAG,
        ASSESSMENT_AMENDMENT,
        DIGEST
    }

    public enum NotificationChannel {
        EMAIL, SMS, IN_APP
    }

    public enum NotificationStatus {
        PENDING, SENT, FAILED, BOUNCED
    }

    public void markSent(String providerMessageId) {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.providerMessageId = providerMessageId;
        this.attemptCount++;
    }

    public void markFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.failureReason = reason;
        this.attemptCount++;
    }

    public void incrementAttempt() {
        this.attemptCount++;
    }
}
