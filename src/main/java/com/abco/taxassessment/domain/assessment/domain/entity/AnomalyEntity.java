package com.abco.taxassessment.domain.assessment.domain.entity;

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
 * Anomaly detected by the Anomaly Detection Agent (UC-22).
 * Triggers notifications per UC-32 and UC-36.
 */
@Entity
@Table(name = "anomalies")
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
public class AnomalyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "anomaly_type", nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    private AnomalyType anomalyType;

    @Column(name = "severity", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AnomalySeverity severity;

    @Column(name = "detail", nullable = false, columnDefinition = "text")
    private String detail;

    @Column(name = "recommended_action", length = 1000)
    private String recommendedAction;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AnomalyStatus status;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "notification_sent", nullable = false)
    private boolean notificationSent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum AnomalyType {
        AMOUNT_OUTLIER,          // Transaction significantly outside category norms
        ROUND_NUMBER_THRESHOLD,  // Round-number transaction above $600 or $10k CTR threshold
        RAPID_SEQUENTIAL,        // Same amount, rapid succession — possible duplicate or subscription error
        CATEGORY_DRIFT,          // Month-over-month category drift
        REGULATORY_THRESHOLD,    // Cash transaction > $10,000 — CTR filing may be required
        POSSIBLE_DUPLICATE       // Potential transaction duplication
    }

    public enum AnomalySeverity {
        CRITICAL,  // → SMS + Email + in-app (regulatory threshold, >$10k)
        WARNING,   // → Email + in-app
        INFO       // → in-app only
    }

    public enum AnomalyStatus {
        OPEN,
        UNDER_REVIEW,
        RESOLVED,
        DISMISSED
    }

    public void resolve(UUID userId, String note) {
        this.status = AnomalyStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        this.resolvedByUserId = userId;
        this.resolutionNote = note;
    }

    public void markNotificationSent() {
        this.notificationSent = true;
    }
}
