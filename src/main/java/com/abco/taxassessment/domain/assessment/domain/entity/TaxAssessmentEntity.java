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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tax assessment produced by the Aggregation Agent (UC-21) and finalized by
 * the Finalization Agent (UC-24).
 *
 * Payload (JSONB) stores the full structured assessment produced by the AI pipeline.
 * Once finalized, the assessment is immutable — amendments create a new version.
 * Version history is maintained for audit purposes (UC-12, UC-37).
 */
@Entity
@Table(name = "tax_assessments")
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
public class TaxAssessmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "statement_id", nullable = false, updatable = false)
    private UUID statementId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AssessmentStatus status;

    @Column(name = "version", nullable = false)
    private int version;

    /** Full structured assessment as JSONB — authoritative output of the pipeline */
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "total_income", precision = 19, scale = 4)
    private BigDecimal totalIncome;

    @Column(name = "total_expenses", precision = 19, scale = 4)
    private BigDecimal totalExpenses;

    @Column(name = "total_deductible_expenses", precision = 19, scale = 4)
    private BigDecimal totalDeductibleExpenses;

    @Column(name = "net_profit_loss", precision = 19, scale = 4)
    private BigDecimal netProfitLoss;

    @Column(name = "estimated_federal_tax", precision = 19, scale = 4)
    private BigDecimal estimatedFederalTax;

    @Column(name = "estimated_state_tax", precision = 19, scale = 4)
    private BigDecimal estimatedStateTax;

    @Column(name = "overall_confidence", precision = 5, scale = 4)
    private BigDecimal overallConfidence;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    /** SHA-256 of the final payload — ensures tamper-evidence of finalized assessments */
    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "prior_version_id")
    private UUID priorVersionId;

    @Column(name = "review_notes", columnDefinition = "text")
    private String reviewNotes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum AssessmentStatus {
        DRAFT,
        PENDING_REVIEW,
        UNDER_REVIEW,
        FINALIZED,
        ARCHIVED,
        AMENDMENT_IN_PROGRESS
    }

    public void finalize(String payloadHash) {
        this.status = AssessmentStatus.FINALIZED;
        this.finalizedAt = Instant.now();
        this.payloadHash = payloadHash;
    }

    public void archive() {
        this.status = AssessmentStatus.ARCHIVED;
    }

    public void submitForReview() {
        this.status = AssessmentStatus.PENDING_REVIEW;
    }
}
