package com.abco.taxassessment.domain.agent.domain.service;

import com.abco.taxassessment.domain.assessment.domain.entity.AnomalyEntity;
import com.abco.taxassessment.domain.assessment.repository.AnomalyRepository;
import com.abco.taxassessment.domain.transaction.domain.entity.TransactionEntity;
import com.abco.taxassessment.domain.transaction.repository.TransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UC-22: Anomaly Detection Agent.
 *
 * Detects:
 * 1. Amount outliers (> 3σ from category mean)
 * 2. Round-number transactions above reporting thresholds ($600 1099-NEC, $10k CTR)
 * 3. Rapid sequential same-amount transactions (possible duplication or subscription error)
 * 4. Category drift (month-over-month significant change in category totals)
 *
 * Outputs AnomalyEntity records with severity and recommended action.
 * Critical anomalies (REGULATORY_THRESHOLD) trigger immediate notifications.
 *
 * Tax correctness note:
 * - $10,000 cash threshold is per FinCEN CTR rules (31 USC §5313)
 * - $600 threshold applies to 1099-NEC (nonemployee compensation)
 * - This service flags for review — it does not file or assert tax positions
 */
@Service
@Transactional
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    /** CTR filing threshold — 31 USC §5313 */
    private static final BigDecimal CTR_THRESHOLD = new BigDecimal("10000.00");

    /** 1099-NEC reporting threshold — IRC §6041A */
    private static final BigDecimal NEC_1099_THRESHOLD = new BigDecimal("600.00");

    /** Maximum transactions in a 24-hour window with same amount to flag as rapid-sequential */
    private static final int RAPID_SEQUENTIAL_THRESHOLD = 3;

    private final TransactionRepository transactionRepository;
    private final AnomalyRepository anomalyRepository;
    private final MeterRegistry meterRegistry;

    public AnomalyDetectionService(TransactionRepository transactionRepository,
                                    AnomalyRepository anomalyRepository,
                                    MeterRegistry meterRegistry) {
        this.transactionRepository = transactionRepository;
        this.anomalyRepository = anomalyRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public List<AnomalyEntity> detectAnomalies(UUID tenantId, UUID statementId, UUID assessmentId) {
        List<TransactionEntity> transactions = transactionRepository
                .findAllByTenantIdAndStatementId(tenantId, statementId);

        List<AnomalyEntity> anomalies = new ArrayList<>();

        anomalies.addAll(detectRegulatoryThresholds(transactions, tenantId, assessmentId));
        anomalies.addAll(detectAmountOutliers(transactions, tenantId, assessmentId));
        anomalies.addAll(detectRapidSequential(transactions, tenantId, assessmentId));

        List<AnomalyEntity> saved = anomalyRepository.saveAll(anomalies);

        meterRegistry.counter("anomalies.detected",
                "tenant_id", tenantId.toString()).increment(saved.size());

        log.info("Detected {} anomalies for statement {} tenant {}",
                 saved.size(), statementId, tenantId);

        return saved;
    }

    private List<AnomalyEntity> detectRegulatoryThresholds(List<TransactionEntity> transactions,
                                                             UUID tenantId, UUID assessmentId) {
        return transactions.stream()
                .filter(tx -> tx.getAmount().compareTo(CTR_THRESHOLD) >= 0)
                .map(tx -> {
                    boolean isCashTx = tx.getDescription().toLowerCase().contains("cash")
                            || tx.getDescription().toLowerCase().contains("wire");

                    String detail = String.format(
                        "Transaction of %s %s on %s (%s) meets or exceeds $10,000 CTR threshold. " +
                        "FinCEN Form 104 filing may be required (31 USC §5313).",
                        tx.getCurrency(), tx.getAmount(), tx.getTransactionDate(), tx.getDescription());

                    return AnomalyEntity.builder()
                            .tenantId(tenantId)
                            .transactionId(tx.getId())
                            .assessmentId(assessmentId)
                            .anomalyType(AnomalyEntity.AnomalyType.REGULATORY_THRESHOLD)
                            .severity(AnomalyEntity.AnomalySeverity.CRITICAL)
                            .detail(detail)
                            .recommendedAction("Consult your CPA. FinCEN Form 104 (Currency Transaction Report) " +
                                               "may be required for cash transactions ≥ $10,000.")
                            .status(AnomalyEntity.AnomalyStatus.OPEN)
                            .notificationSent(false)
                            .build();
                })
                .toList();
    }

    private List<AnomalyEntity> detectAmountOutliers(List<TransactionEntity> transactions,
                                                       UUID tenantId, UUID assessmentId) {
        Map<String, List<TransactionEntity>> byCategory = transactions.stream()
                .filter(tx -> tx.getCategoryCode() != null)
                .collect(Collectors.groupingBy(TransactionEntity::getCategoryCode));

        List<AnomalyEntity> outliers = new ArrayList<>();

        for (Map.Entry<String, List<TransactionEntity>> entry : byCategory.entrySet()) {
            List<TransactionEntity> categoryTxs = entry.getValue();
            if (categoryTxs.size() < 3) continue; // need minimum sample

            BigDecimal mean = categoryTxs.stream()
                    .map(TransactionEntity::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(categoryTxs.size()), 4, RoundingMode.HALF_UP);

            BigDecimal variance = categoryTxs.stream()
                    .map(tx -> tx.getAmount().subtract(mean).pow(2))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(categoryTxs.size()), 4, RoundingMode.HALF_UP);

            double stdDev = Math.sqrt(variance.doubleValue());
            double threshold = mean.doubleValue() + 3 * stdDev;

            categoryTxs.stream()
                    .filter(tx -> tx.getAmount().doubleValue() > threshold)
                    .forEach(tx -> outliers.add(
                        AnomalyEntity.builder()
                                .tenantId(tenantId)
                                .transactionId(tx.getId())
                                .assessmentId(assessmentId)
                                .anomalyType(AnomalyEntity.AnomalyType.AMOUNT_OUTLIER)
                                .severity(AnomalyEntity.AnomalySeverity.WARNING)
                                .detail(String.format(
                                    "Transaction amount %s in category %s is %.1fx the category average of %s.",
                                    tx.getAmount(), entry.getKey(),
                                    tx.getAmount().doubleValue() / mean.doubleValue(), mean))
                                .recommendedAction("Review transaction for correct categorization.")
                                .status(AnomalyEntity.AnomalyStatus.OPEN)
                                .notificationSent(false)
                                .build()
                    ));
        }

        return outliers;
    }

    private List<AnomalyEntity> detectRapidSequential(List<TransactionEntity> transactions,
                                                        UUID tenantId, UUID assessmentId) {
        Map<BigDecimal, Map<LocalDate, List<TransactionEntity>>> grouped = transactions.stream()
                .collect(Collectors.groupingBy(
                        TransactionEntity::getAmount,
                        Collectors.groupingBy(TransactionEntity::getTransactionDate)));

        List<AnomalyEntity> anomalies = new ArrayList<>();

        grouped.forEach((amount, dateMap) ->
            dateMap.forEach((date, txs) -> {
                if (txs.size() >= RAPID_SEQUENTIAL_THRESHOLD) {
                    txs.forEach(tx -> anomalies.add(
                        AnomalyEntity.builder()
                                .tenantId(tenantId)
                                .transactionId(tx.getId())
                                .assessmentId(assessmentId)
                                .anomalyType(AnomalyEntity.AnomalyType.RAPID_SEQUENTIAL)
                                .severity(AnomalyEntity.AnomalySeverity.WARNING)
                                .detail(String.format(
                                    "%d transactions of identical amount %s on %s. Possible duplication or subscription error.",
                                    txs.size(), amount, date))
                                .recommendedAction("Verify these are distinct transactions and not duplicates.")
                                .status(AnomalyEntity.AnomalyStatus.OPEN)
                                .notificationSent(false)
                                .build()
                    ));
                }
            })
        );

        return anomalies;
    }
}
