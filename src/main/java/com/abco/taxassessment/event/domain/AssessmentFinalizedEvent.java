package com.abco.taxassessment.event.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Published when a tax assessment is finalized (UC-24). Triggers notifications (UC-31). */
public class AssessmentFinalizedEvent extends BaseAgentEvent {

    private UUID assessmentId;
    private UUID statementId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal netProfitLoss;
    private int transactionCount;

    public AssessmentFinalizedEvent() {}

    public AssessmentFinalizedEvent(UUID tenantId, UUID assessmentId, UUID statementId,
                                     LocalDate periodStart, LocalDate periodEnd,
                                     BigDecimal totalIncome, BigDecimal totalExpenses,
                                     BigDecimal netProfitLoss, int transactionCount) {
        super("assessment.finalized", 1, tenantId, "TaxAssessment", assessmentId);
        this.assessmentId = assessmentId;
        this.statementId = statementId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.totalIncome = totalIncome;
        this.totalExpenses = totalExpenses;
        this.netProfitLoss = netProfitLoss;
        this.transactionCount = transactionCount;
    }

    public UUID getAssessmentId() { return assessmentId; }
    public UUID getStatementId() { return statementId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public BigDecimal getTotalIncome() { return totalIncome; }
    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public BigDecimal getNetProfitLoss() { return netProfitLoss; }
    public int getTransactionCount() { return transactionCount; }

    public void setAssessmentId(UUID assessmentId) { this.assessmentId = assessmentId; }
    public void setStatementId(UUID statementId) { this.statementId = statementId; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public void setTotalIncome(BigDecimal totalIncome) { this.totalIncome = totalIncome; }
    public void setTotalExpenses(BigDecimal totalExpenses) { this.totalExpenses = totalExpenses; }
    public void setNetProfitLoss(BigDecimal netProfitLoss) { this.netProfitLoss = netProfitLoss; }
    public void setTransactionCount(int transactionCount) { this.transactionCount = transactionCount; }
}
