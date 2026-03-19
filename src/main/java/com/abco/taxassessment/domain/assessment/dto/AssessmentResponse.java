package com.abco.taxassessment.domain.assessment.dto;

import com.abco.taxassessment.domain.assessment.domain.entity.TaxAssessmentEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Tax assessment resource")
public record AssessmentResponse(
        UUID id,
        UUID tenantId,
        UUID statementId,
        LocalDate periodStart,
        LocalDate periodEnd,
        TaxAssessmentEntity.AssessmentStatus status,
        int version,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal totalDeductibleExpenses,
        BigDecimal netProfitLoss,
        BigDecimal estimatedFederalTax,
        BigDecimal estimatedStateTax,
        BigDecimal overallConfidence,
        Instant finalizedAt,
        String payloadHash,
        UUID priorVersionId,
        Instant createdAt,
        Instant updatedAt
) {}
