package com.abco.taxassessment.domain.statement.domain.entity;

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
import java.time.LocalDate;
import java.util.UUID;

/**
 * A bank statement file ingested for a tenant (UC-07, UC-08).
 * Tracks processing lifecycle from UPLOADED through FINALIZED.
 */
@Entity
@Table(name = "statements")
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
public class StatementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private StatementStatus status;

    @Column(name = "source_format", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SourceFormat sourceFormat;

    @Column(name = "was_partial", nullable = false)
    private boolean wasPartial;

    @Column(name = "file_path_encrypted", length = 1024)
    private String filePathEncrypted;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "original_filename", length = 512)
    private String originalFilename;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "transaction_count")
    private Integer transactionCount;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "is_amendment", nullable = false)
    private boolean amendment;

    @Column(name = "amends_statement_id")
    private UUID amendsStatementId;

    @Column(name = "version", nullable = false)
    private int version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum StatementStatus {
        UPLOADED,
        DUPLICATE_QUARANTINED,
        INGESTION_FAILED,
        PROCESSING,
        PROCESSED,
        REVIEW_REQUIRED,
        FINALIZED,
        ARCHIVED
    }

    public enum SourceFormat {
        PDF, CSV, OFX, QFX, MT940
    }

    public void transitionTo(StatementStatus newStatus) {
        this.status = newStatus;
    }

    public void markFailed(String reason) {
        this.status = StatementStatus.INGESTION_FAILED;
        this.failureReason = reason;
    }

    public void setTransactionCount(int count) {
        this.transactionCount = count;
    }
}
