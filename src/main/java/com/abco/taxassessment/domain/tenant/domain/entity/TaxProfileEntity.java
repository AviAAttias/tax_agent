package com.abco.taxassessment.domain.tenant.domain.entity;

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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.Month;
import java.util.UUID;

/**
 * Tax profile for a tenant — entity type, fiscal year, jurisdiction, accounting method.
 * Used by agents to apply correct categorization and tax rules (UC-02, UC-19).
 * tenantId is included for row-level security (§8 Layer 1).
 */
@Entity
@Table(name = "tax_profiles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "entity_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    @Column(name = "fiscal_year_start_month", nullable = false)
    @Enumerated(EnumType.STRING)
    private Month fiscalYearStartMonth;

    /** JSON array of jurisdiction codes, e.g. ["US-CA", "US-NY"] */
    @Column(name = "jurisdictions", nullable = false, columnDefinition = "jsonb")
    private String jurisdictions;

    @Column(name = "accounting_method", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccountingMethod accountingMethod;

    /** NAICS 6-digit code, e.g. "541511" (Custom Computer Programming Services) */
    @Column(name = "naics_code", length = 10)
    private String naicsCode;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum EntityType {
        SOLE_PROP,    // Schedule C
        LLC_SINGLE,   // Schedule C (disregarded)
        LLC_MULTI,    // Form 1065
        S_CORP,       // Form 1120-S
        C_CORP        // Form 1120
    }

    public enum AccountingMethod {
        CASH, ACCRUAL
    }
}
