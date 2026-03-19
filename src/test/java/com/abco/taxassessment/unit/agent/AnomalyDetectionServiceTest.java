package com.abco.taxassessment.unit.agent;

import com.abco.taxassessment.domain.agent.domain.service.AnomalyDetectionService;
import com.abco.taxassessment.domain.assessment.domain.entity.AnomalyEntity;
import com.abco.taxassessment.domain.assessment.repository.AnomalyRepository;
import com.abco.taxassessment.domain.transaction.domain.entity.TransactionEntity;
import com.abco.taxassessment.domain.transaction.repository.TransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for AnomalyDetectionService (§17.1).
 * Verifies correct anomaly classification per IRS rules.
 */
@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AnomalyRepository anomalyRepository;

    @Captor
    private ArgumentCaptor<List<AnomalyEntity>> anomalyCaptor;

    private AnomalyDetectionService anomalyDetectionService;
    private UUID tenantId;
    private UUID statementId;
    private UUID assessmentId;

    @BeforeEach
    void setUp() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        anomalyDetectionService = new AnomalyDetectionService(
                transactionRepository, anomalyRepository, meterRegistry);
        tenantId = UUID.randomUUID();
        statementId = UUID.randomUUID();
        assessmentId = UUID.randomUUID();
    }

    @Test
    void detectAnomalies_transactionAbove10k_flaggedAsCriticalRegulatoryThreshold() {
        // 31 USC §5313: $10,000 CTR threshold
        TransactionEntity largeTx = buildTransaction("CASH DEPOSIT", new BigDecimal("15000.00"),
                TransactionEntity.TransactionSign.CREDIT);

        given(transactionRepository.findAllByTenantIdAndStatementId(tenantId, statementId))
                .willReturn(List.of(largeTx));
        given(anomalyRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        anomalyDetectionService.detectAnomalies(tenantId, statementId, assessmentId);

        verify(anomalyRepository).saveAll(anomalyCaptor.capture());
        List<AnomalyEntity> saved = anomalyCaptor.getValue();

        assertThat(saved).anyMatch(a ->
                a.getAnomalyType() == AnomalyEntity.AnomalyType.REGULATORY_THRESHOLD
                && a.getSeverity() == AnomalyEntity.AnomalySeverity.CRITICAL);
    }

    @Test
    void detectAnomalies_exactlyAtThreshold_isFlagged() {
        // Boundary condition: exactly $10,000 must also trigger CTR flag
        TransactionEntity atThresholdTx = buildTransaction("WIRE TRANSFER",
                new BigDecimal("10000.00"), TransactionEntity.TransactionSign.CREDIT);

        given(transactionRepository.findAllByTenantIdAndStatementId(tenantId, statementId))
                .willReturn(List.of(atThresholdTx));
        given(anomalyRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        anomalyDetectionService.detectAnomalies(tenantId, statementId, assessmentId);

        verify(anomalyRepository).saveAll(anomalyCaptor.capture());
        assertThat(anomalyCaptor.getValue()).anyMatch(a ->
                a.getAnomalyType() == AnomalyEntity.AnomalyType.REGULATORY_THRESHOLD);
    }

    @Test
    void detectAnomalies_belowThreshold_notFlaggedAsRegulatory() {
        TransactionEntity normalTx = buildTransaction("PAYMENT",
                new BigDecimal("9999.99"), TransactionEntity.TransactionSign.CREDIT);

        given(transactionRepository.findAllByTenantIdAndStatementId(tenantId, statementId))
                .willReturn(List.of(normalTx));
        given(anomalyRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        anomalyDetectionService.detectAnomalies(tenantId, statementId, assessmentId);

        verify(anomalyRepository).saveAll(anomalyCaptor.capture());
        assertThat(anomalyCaptor.getValue()).noneMatch(a ->
                a.getAnomalyType() == AnomalyEntity.AnomalyType.REGULATORY_THRESHOLD);
    }

    @Test
    void detectAnomalies_3sameAmountSameDay_flaggedAsRapidSequential() {
        LocalDate date = LocalDate.of(2024, 1, 15);
        TransactionEntity tx1 = buildTransactionOnDate("AMAZON CHARGE", new BigDecimal("99.99"),
                TransactionEntity.TransactionSign.DEBIT, date);
        TransactionEntity tx2 = buildTransactionOnDate("AMAZON CHARGE", new BigDecimal("99.99"),
                TransactionEntity.TransactionSign.DEBIT, date);
        TransactionEntity tx3 = buildTransactionOnDate("AMAZON CHARGE", new BigDecimal("99.99"),
                TransactionEntity.TransactionSign.DEBIT, date);

        given(transactionRepository.findAllByTenantIdAndStatementId(tenantId, statementId))
                .willReturn(List.of(tx1, tx2, tx3));
        given(anomalyRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        anomalyDetectionService.detectAnomalies(tenantId, statementId, assessmentId);

        verify(anomalyRepository).saveAll(anomalyCaptor.capture());
        assertThat(anomalyCaptor.getValue()).anyMatch(a ->
                a.getAnomalyType() == AnomalyEntity.AnomalyType.RAPID_SEQUENTIAL
                && a.getSeverity() == AnomalyEntity.AnomalySeverity.WARNING);
    }

    private TransactionEntity buildTransaction(String description, BigDecimal amount,
                                                TransactionEntity.TransactionSign sign) {
        return buildTransactionOnDate(description, amount, sign, LocalDate.of(2024, 1, 15));
    }

    private TransactionEntity buildTransactionOnDate(String description, BigDecimal amount,
                                                      TransactionEntity.TransactionSign sign,
                                                      LocalDate date) {
        return TransactionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .statementId(statementId)
                .transactionDate(date)
                .description(description)
                .amount(amount)
                .sign(sign)
                .currency("USD")
                .status(TransactionEntity.TransactionStatus.TAX_RULES_APPLIED)
                .interaccountTransfer(false)
                .flaggedForReview(false)
                .overrideApplied(false)
                .build();
    }
}
