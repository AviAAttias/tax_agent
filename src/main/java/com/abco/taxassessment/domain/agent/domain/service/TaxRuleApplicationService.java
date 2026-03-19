package com.abco.taxassessment.domain.agent.domain.service;

import com.abco.taxassessment.domain.transaction.domain.entity.TransactionEntity;
import com.abco.taxassessment.domain.transaction.domain.entity.TaxCategoryEntity;
import com.abco.taxassessment.domain.transaction.repository.TransactionRepository;
import com.abco.taxassessment.domain.transaction.repository.TaxCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UC-19: Tax Rule Application Agent.
 *
 * Applies jurisdiction-specific tax rules to categorized transactions:
 * - Meals: 50% deductibility limit §274(n)
 * - Home office: proportional deduction (requires Form 8829 flag)
 * - Vehicle: mileage vs. actual expense election
 * - Capital purchases: depreciation schedule vs. Section 179 expensing
 * - Owner's draws: non-deductible, separated from salary
 * - Interaccount transfers: excluded from income/expense
 *
 * Tax correctness is the primary constraint. This service does not provide legal advice
 * (per non-goals), but applies rules mechanically based on IRS publications.
 * Ambiguous cases are flagged for CPA review.
 */
@Service
@Transactional
public class TaxRuleApplicationService {

    private static final Logger log = LoggerFactory.getLogger(TaxRuleApplicationService.class);

    private final TransactionRepository transactionRepository;
    private final TaxCategoryRepository taxCategoryRepository;

    public TaxRuleApplicationService(TransactionRepository transactionRepository,
                                      TaxCategoryRepository taxCategoryRepository) {
        this.transactionRepository = transactionRepository;
        this.taxCategoryRepository = taxCategoryRepository;
    }

    @Transactional
    public void applyTaxRules(UUID tenantId, UUID statementId) {
        List<TransactionEntity> transactions = transactionRepository
                .findAllByTenantIdAndStatementId(tenantId, statementId);

        Map<String, TaxCategoryEntity> categoryMap = taxCategoryRepository.findAll()
                .stream()
                .collect(Collectors.toMap(TaxCategoryEntity::getCode, Function.identity()));

        for (TransactionEntity tx : transactions) {
            if (tx.getCategoryCode() == null) {
                tx.flagForReview("No category assigned — cannot apply tax rules");
                transactionRepository.save(tx);
                continue;
            }

            applyRuleForTransaction(tx, categoryMap.get(tx.getCategoryCode()));
            transactionRepository.save(tx);
        }

        log.info("Applied tax rules to {} transactions for statement {}", transactions.size(), statementId);
    }

    private void applyRuleForTransaction(TransactionEntity tx, TaxCategoryEntity category) {
        if (category == null) {
            tx.flagForReview("Unknown category code: " + tx.getCategoryCode());
            return;
        }

        if (category.isRequiresHumanReview()) {
            tx.flagForReview("Category " + category.getName() + " requires CPA review");
        }

        TransactionEntity.TaxTreatment treatment;
        BigDecimal deductibleAmount;

        switch (category.getDeductibilityTier()) {
            case FULLY_DEDUCTIBLE -> {
                treatment = tx.getSign() == TransactionEntity.TransactionSign.DEBIT
                        ? TransactionEntity.TaxTreatment.FULLY_DEDUCTIBLE_EXPENSE
                        : TransactionEntity.TaxTreatment.INCOME;
                deductibleAmount = tx.getSign() == TransactionEntity.TransactionSign.DEBIT
                        ? tx.getAmount() : BigDecimal.ZERO;
            }
            case FIFTY_PERCENT -> {
                // §274(n): meals are 50% deductible
                treatment = TransactionEntity.TaxTreatment.FIFTY_PERCENT_DEDUCTIBLE;
                deductibleAmount = tx.getAmount().multiply(BigDecimal.valueOf(0.5))
                        .setScale(4, RoundingMode.HALF_UP);
            }
            case PROPORTIONAL -> {
                // Home office, vehicle actual — proportional deduction requires Form 8829 or Form 4562
                treatment = TransactionEntity.TaxTreatment.PROPORTIONAL_DEDUCTIBLE;
                // Deductible amount cannot be calculated without home office ratio / business use %
                // Flag for review with the full amount as the gross figure
                deductibleAmount = BigDecimal.ZERO;
                tx.flagForReview("Proportional deduction requires business use percentage. Full amount: "
                                 + tx.getAmount());
            }
            case DEPRECIATION -> {
                treatment = TransactionEntity.TaxTreatment.DEPRECIATION_REQUIRED;
                // Capital asset — not immediately deductible; depreciation schedule required
                deductibleAmount = BigDecimal.ZERO;
                tx.flagForReview("Capital purchase requires depreciation schedule (Form 4562). "
                                + "Section 179 election may apply if asset cost ≤ §179 limit.");
            }
            case SECTION_179 -> {
                treatment = TransactionEntity.TaxTreatment.SECTION_179;
                // Section 179 allows immediate expensing — but subject to income limitation
                deductibleAmount = tx.getAmount();
                tx.flagForReview("Section 179 election: verify income limitation applies. "
                                + "Report on Form 4562.");
            }
            case NON_DEDUCTIBLE, SUBJECT_TO_PHASEOUT -> {
                treatment = TransactionEntity.TaxTreatment.NON_DEDUCTIBLE;
                deductibleAmount = BigDecimal.ZERO;
                if (category.getCode().startsWith("OWNER_DRAW")) {
                    treatment = TransactionEntity.TaxTreatment.NON_DEDUCTIBLE;
                }
            }
            default -> {
                treatment = TransactionEntity.TaxTreatment.PENDING_REVIEW;
                deductibleAmount = BigDecimal.ZERO;
                tx.flagForReview("Unknown deductibility tier: " + category.getDeductibilityTier());
            }
        }

        tx.applyTaxTreatment(treatment, deductibleAmount);
    }
}
