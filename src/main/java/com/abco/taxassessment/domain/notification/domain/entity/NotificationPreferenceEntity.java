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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant-specific notification preference per event type and channel (UC-06).
 * Channel routing is driven by this table, not hardcoded (§Notification System).
 */
@Entity
@Table(name = "notification_preferences")
@EntityListeners(AuditingEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    private NotificationEventEntity.NotificationEventType eventType;

    @Column(name = "channel", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private NotificationEventEntity.NotificationChannel channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "frequency", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private NotificationFrequency frequency;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum NotificationFrequency {
        IMMEDIATE, DAILY_DIGEST, WEEKLY_DIGEST
    }

    public boolean isDigest() {
        return frequency == NotificationFrequency.DAILY_DIGEST
            || frequency == NotificationFrequency.WEEKLY_DIGEST;
    }
}
