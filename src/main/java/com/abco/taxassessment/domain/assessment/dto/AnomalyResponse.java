package com.abco.taxassessment.domain.assessment.dto;

import com.abco.taxassessment.domain.assessment.domain.entity.AnomalyEntity;

import java.time.Instant;
import java.util.UUID;

public record AnomalyResponse(
        UUID id,
        UUID tenantId,
        UUID transactionId,
        UUID assessmentId,
        AnomalyEntity.AnomalyType anomalyType,
        AnomalyEntity.AnomalySeverity severity,
        String detail,
        String recommendedAction,
        AnomalyEntity.AnomalyStatus status,
        Instant resolvedAt,
        Instant createdAt
) {}
