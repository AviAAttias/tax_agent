package com.abco.taxassessment.domain.statement.dto;

import com.abco.taxassessment.domain.statement.domain.entity.StatementEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Bank statement resource")
public record StatementResponse(
        @Schema(description = "Statement unique identifier")
        UUID id,
        UUID tenantId,
        UUID accountId,
        LocalDate periodStart,
        LocalDate periodEnd,
        StatementEntity.StatementStatus status,
        StatementEntity.SourceFormat sourceFormat,
        boolean wasPartial,
        String originalFilename,
        Long fileSizeBytes,
        Integer transactionCount,
        boolean amendment,
        UUID amendsStatementId,
        int version,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {}
