package com.abco.taxassessment.domain.agent.domain.entity;

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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks the state of each agent pipeline job (UC-15 through UC-24).
 *
 * Orchestration state is tracked in this table — NOT inferred from queue depth (§Architecture).
 * This enables:
 *   - Accurate pipeline progress reporting
 *   - Retry tracking and DLQ routing
 *   - Audit trail of all processing stages
 */
@Entity
@Table(name = "agent_jobs")
@EntityListeners(AuditingEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "statement_id", nullable = false, updatable = false)
    private UUID statementId;

    @Column(name = "agent_name", nullable = false, length = 100)
    @Enumerated(EnumType.STRING)
    private AgentName agentName;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AgentJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "input_event_id", length = 255)
    private String inputEventId;

    @Column(name = "output_event_id", length = 255)
    private String outputEventId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum AgentName {
        DOCUMENT_CLASSIFICATION,
        OCR_EXTRACTION,
        TRANSACTION_PARSING,
        TRANSACTION_CATEGORIZATION,
        TAX_RULE_APPLICATION,
        RECONCILIATION,
        ASSESSMENT_AGGREGATION,
        ANOMALY_DETECTION,
        ASSESSMENT_REVIEW,
        ASSESSMENT_FINALIZATION
    }

    public enum AgentJobStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        DLQ_ROUTED,
        REVIEW_REQUIRED
    }

    public void start(String inputEventId) {
        this.status = AgentJobStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
        this.inputEventId = inputEventId;
        this.attemptCount++;
    }

    public void complete(String outputEventId) {
        this.status = AgentJobStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.outputEventId = outputEventId;
    }

    public void fail(String errorMessage) {
        this.status = AgentJobStatus.FAILED;
        this.lastError = errorMessage;
    }

    public void routeToDlq(String errorMessage) {
        this.status = AgentJobStatus.DLQ_ROUTED;
        this.lastError = errorMessage;
    }
}
