package com.abco.taxassessment.domain.transaction.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Tax category hierarchy — two-level (parent/child).
 * This entity is NOT tenant-owned; it is a platform-level reference table.
 * Seeded via Liquibase changeset covering Schedule C, Schedule E, and common business expenses.
 *
 * Examples:
 *   code=REVENUE, parent=null (top-level)
 *   code=REVENUE_SALES, parent=REVENUE, scheduleLine=1a (Schedule C)
 *   code=EXPENSE_MEALS, parent=EXPENSE, deductibilityTier=FIFTY_PERCENT, scheduleLine=24b
 *   code=EXPENSE_HOME_OFFICE, parent=EXPENSE, deductibilityTier=PROPORTIONAL, scheduleLine=30
 */
@Entity
@Table(name = "tax_categories")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaxCategoryEntity {

    @Id
    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "parent_code", length = 100)
    private String parentCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "deductibility_tier", length = 50)
    @Enumerated(EnumType.STRING)
    private DeductibilityTier deductibilityTier;

    /** Maximum deductibility percentage, e.g. 50.00 for meals, 100.00 for fully deductible */
    @Column(name = "deductibility_percentage", precision = 5, scale = 2)
    private BigDecimal deductibilityPercentage;

    /** IRS form line reference, e.g. "24b" for Schedule C meals */
    @Column(name = "schedule_line", length = 20)
    private String scheduleLine;

    /** IRS form, e.g. "SCHEDULE_C", "SCHEDULE_E", "FORM_1065" */
    @Column(name = "tax_form", length = 50)
    private String taxForm;

    /** Whether AI can auto-categorize here, or human review is always required */
    @Column(name = "requires_human_review", nullable = false)
    private boolean requiresHumanReview;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public enum DeductibilityTier {
        FULLY_DEDUCTIBLE,
        FIFTY_PERCENT,       // Meals §274(n)
        PROPORTIONAL,        // Home office, vehicle actual expense
        DEPRECIATION,        // Capital assets — must apply depreciation schedule
        SECTION_179,         // Immediate expensing election
        NON_DEDUCTIBLE,      // Owner draws, personal expenses
        SUBJECT_TO_PHASEOUT  // Entertainment (fully non-deductible post-TCJA)
    }
}
