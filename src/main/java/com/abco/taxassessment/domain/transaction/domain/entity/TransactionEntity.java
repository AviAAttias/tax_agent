package com.abco.taxassessment.domain.transaction.domain.entity;

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
 * A single transaction extracted from a bank statement.
 * Central entity in the tax assessment pipeline.
 *
 * FX handling (UC-10): original currency and amount are preserved.
 * baseCurrencyAmount stores the converted amount for tax reporting.
 * fxRateApplied stores the rate used (fetched for transaction date, immutable once set).
 */
@Entity
@Table(name = "transactions")
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
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "statement_id", nullable = false, updatable = false)
    private UUID statementId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "sign", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private TransactionSign sign;

    @Column(name = "running_balance", precision = 19, scale = 4)
    private BigDecimal runningBalance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "base_currency_amount", precision = 19, scale = 4)
    private BigDecimal baseCurrencyAmount;

    @Column(name = "fx_rate_applied", precision = 19, scale = 8)
    private BigDecimal fxRateApplied;

    @Column(name = "raw_line", columnDefinition = "text")
    private String rawLine;

    @Column(name = "category_code", length = 100)
    private String categoryCode;

    @Column(name = "category_confidence", precision = 5, scale = 4)
    private BigDecimal categoryConfidence;

    @Column(name = "tax_treatment", length = 50)
    @Enumerated(EnumType.STRING)
    private TaxTreatment taxTreatment;

    @Column(name = "deductible_amount", precision = 19, scale = 4)
    private BigDecimal deductibleAmount;

    @Column(name = "is_interaccount_transfer", nullable = false)
    private boolean interaccountTransfer;

    @Column(name = "is_flagged_for_review", nullable = false)
    private boolean flaggedForReview;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "is_override_applied", nullable = false)
    private boolean overrideApplied;

    @Column(name = "override_category_code", length = 100)
    private String overrideCategoryCode;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum TransactionSign {
        DEBIT, CREDIT
    }

    public enum TaxTreatment {
        INCOME,
        FULLY_DEDUCTIBLE_EXPENSE,
        FIFTY_PERCENT_DEDUCTIBLE,
        PROPORTIONAL_DEDUCTIBLE,
        DEPRECIATION_REQUIRED,
        SECTION_179,
        NON_DEDUCTIBLE,
        INTERACCOUNT_TRANSFER,
        PENDING_REVIEW
    }

    public enum TransactionStatus {
        PARSED,
        CATEGORIZED,
        TAX_RULES_APPLIED,
        RECONCILED,
        FINALIZED,
        FLAGGED_FOR_REVIEW
    }

    public void applyCategory(String code, BigDecimal confidence) {
        this.categoryCode = code;
        this.categoryConfidence = confidence;
        this.status = TransactionStatus.CATEGORIZED;
    }

    public void applyTaxTreatment(TaxTreatment treatment, BigDecimal deductibleAmount) {
        this.taxTreatment = treatment;
        this.deductibleAmount = deductibleAmount;
        this.status = TransactionStatus.TAX_RULES_APPLIED;
    }

    public void markAsInteraccountTransfer() {
        this.interaccountTransfer = true;
        this.taxTreatment = TaxTreatment.INTERACCOUNT_TRANSFER;
    }

    public void flagForReview(String reason) {
        this.flaggedForReview = true;
        this.reviewReason = reason;
        this.status = TransactionStatus.FLAGGED_FOR_REVIEW;
    }

    public void applyOverride(String overrideCategoryCode) {
        this.overrideApplied = true;
        this.overrideCategoryCode = overrideCategoryCode;
        this.categoryCode = overrideCategoryCode;
        this.categoryConfidence = BigDecimal.ONE;
    }
}
