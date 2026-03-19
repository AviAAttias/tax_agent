package com.abco.taxassessment.unit.service;

import com.abco.taxassessment.domain.agent.domain.service.TaxRuleApplicationService;
import com.abco.taxassessment.domain.transaction.domain.entity.TaxCategoryEntity;
import com.abco.taxassessment.domain.transaction.domain.entity.TransactionEntity;
import com.abco.taxassessment.domain.transaction.repository.TaxCategoryRepository;
import com.abco.taxassessment.domain.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit test for TaxRuleApplicationService (§17.1 — Unit category).
 *
 * Tests are deterministic (no Thread.sleep, no time-dependent assertions).
 * Tests are repeatable (no shared mutable state between tests).
 * Database mocked — domain rule testing only.
 */
@ExtendWith(MockitoExtension.class)
class TaxRuleApplicationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TaxCategoryRepository taxCategoryRepository;

    @InjectMocks
    private TaxRuleApplicationService taxRuleApplicationService;

    private UUID tenantId;
    private UUID statementId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        statementId = UUID.randomUUID();
    }

    @Test
    void applyTaxRules_meals_applied50PercentDeductibility() {
        TransactionEntity mealTx = buildTransaction("UBER EATS - BUSINESS LUNCH",
                new BigDecimal("120.00"), TransactionEntity.TransactionSign.DEBIT);
        mealTx.applyCategory("EXPENSE_MEALS", new BigDecimal("0.92"));

        TaxCategoryEntity mealsCategory = TaxCategoryEntity.builder()
                .code("EXPENSE_MEALS")
                .name("Expenses > Meals (50% Deductible)")
                .deductibilityTier(TaxCategoryEntity.DeductibilityTier.FIFTY_PERCENT)
                .deductibilityPercentage(new BigDecimal("50.00"))
                .requiresHumanReview(false)
                .active(true)
                .build();

        given(transactionRepository.findAllByTenantIdAndStatementId(tenantId, statementId))
                .willReturn(List.of(mealTx));
        given(taxCategoryRepository.findAll()).willReturn(List.of(mealsCategory));
        given(transactionRepository.save(any())).willReturn(mealTx);

        taxRuleApplicationService.applyTaxRules(tenantId, statementId);

        assertThat(mealTx.getTaxTreatment())
                .isEqualTo(TransactionEntity.TaxTreatment.FIFTY_PERCENT_DEDUCTIBLE);
        assertThat(mealTx.getDeductibleAmount())
                .isEqualByComparingTo(new BigDecimal("60.0000")); // 50% of 120.00

        verify(transactionRepository).save(mealTx);
    }

    @Test
    void applyTaxRules_fullyDeductibleExpense_deductedInFull() {
        TransactionEntity softwareTx = buildTransaction("GITHUB SUBSCRIPTION",
                new BigDecimal("10.00"), TransactionEntity.TransactionSign.DEBIT);
        softwareTx.applyCategory("EXPENSE_SOFTWARE", new BigDecimal("0.98"));

        TaxCategoryEntity softwareCategory = TaxCategoryEntity.builder()
                .code("EXPENSE_SOFTWARE")
                .name("Expenses > Software / SaaS")
                .deductibilityTier(TaxCategoryEntity.DeductibilityTier.FULLY_DEDUCTIBLE)
                .deductibilityPercentage(new BigDecimal("100.00"))
                .requiresHumanReview(false)
                .active(true)
                .build();

        given(transactionRepository.findAllByTenantIdAndStatementId(tenantId, statementId))
                .willReturn(List.of(softwareTx));
        given(taxCategoryRepository.findAll()).willReturn(List.of(softwareCategory));
        given(transactionRepository.save(any())).willReturn(softwareTx);

        taxRuleApplicationService.applyTaxRules(tenantId, statementId);

        assertThat(softwareTx.getTaxTreatment())
                .isEqualTo(TransactionEntity.TaxTreatment.FULLY_DEDUCTIBLE_EXPENSE);
        assertThat(softwareTx.getDeductibleAmount())
                .isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void applyTaxRules_creditTransaction_treatedAsIncome() {
        TransactionEntity incomeTx = buildTransaction("STRIPE PAYOUT",
                new BigDecimal("5000.00"), TransactionEntity.TransactionSign.CREDIT);
        incomeTx.applyCategory("REVENUE_SALES", new BigDecimal("0.95"));

        TaxCategoryEntity revenueCategory = TaxCategoryEntity.builder()
                .code("REVENUE_SALES")
                .name("Revenue > Sales")
                .deductibilityTier(TaxCategoryEntity.DeductibilityTier.FULLY_DEDUCTIBLE)
                .deductibilityPercentage(new BigDecimal("100.00"))
                .requiresHumanReview(false)
                .active(true)
                .build();

        given(transactionRepository.findAllByTenantIdAndStatementId(tenantId, statementId))
                .willReturn(List.of(incomeTx));
        given(taxCategoryRepository.findAll()).willReturn(List.of(revenueCategory));
        given(transactionRepository.save(any())).willReturn(incomeTx);

        taxRuleApplicationService.applyTaxRules(tenantId, statementId);

        assertThat(incomeTx.getTaxTreatment()).isEqualTo(TransactionEntity.TaxTreatment.INCOME);
        assertThat(incomeTx.getDeductibleAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void applyTaxRules_proportionalCategory_flaggedForReview() {
        TransactionEntity homeOfficeTx = buildTransaction("HOME RENT PAYMENT",
                new BigDecimal("3000.00"), TransactionEntity.TransactionSign.DEBIT);
        homeOfficeTx.applyCategory("EXPENSE_HOME_OFFICE", new BigDecimal("0.70"));

        TaxCategoryEntity homeOfficeCategory = TaxCategoryEntity.builder()
                .code("EXPENSE_HOME_OFFICE")
                .name("Expenses > Home Office")
                .deductibilityTier(TaxCategoryEntity.DeductibilityTier.PROPORTIONAL)
                .requiresHumanReview(true)
                .active(true)
                .build();

        given(transactionRepository.findAllByTenantIdAndStatementId(tenantId, statementId))
                .willReturn(List.of(homeOfficeTx));
        given(taxCategoryRepository.findAll()).willReturn(List.of(homeOfficeCategory));
        given(transactionRepository.save(any())).willReturn(homeOfficeTx);

        taxRuleApplicationService.applyTaxRules(tenantId, statementId);

        assertThat(homeOfficeTx.getTaxTreatment())
                .isEqualTo(TransactionEntity.TaxTreatment.PROPORTIONAL_DEDUCTIBLE);
        assertThat(homeOfficeTx.isFlaggedForReview()).isTrue();
        assertThat(homeOfficeTx.getReviewReason()).contains("Proportional deduction");
    }

    @Test
    void applyTaxRules_nocategoryAssigned_flaggedForReview() {
        TransactionEntity uncategorized = buildTransaction("UNKNOWN MERCHANT",
                new BigDecimal("50.00"), TransactionEntity.TransactionSign.DEBIT);
        // No category assigned — categoryCode is null

        given(transactionRepository.findAllByTenantIdAndStatementId(tenantId, statementId))
                .willReturn(List.of(uncategorized));
        given(taxCategoryRepository.findAll()).willReturn(List.of());
        given(transactionRepository.save(any())).willReturn(uncategorized);

        taxRuleApplicationService.applyTaxRules(tenantId, statementId);

        assertThat(uncategorized.isFlaggedForReview()).isTrue();
    }

    private TransactionEntity buildTransaction(String description, BigDecimal amount,
                                                TransactionEntity.TransactionSign sign) {
        return TransactionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .statementId(statementId)
                .transactionDate(LocalDate.of(2024, 1, 15))
                .description(description)
                .amount(amount)
                .sign(sign)
                .currency("USD")
                .status(TransactionEntity.TransactionStatus.PARSED)
                .interaccountTransfer(false)
                .flaggedForReview(false)
                .overrideApplied(false)
                .build();
    }
}
