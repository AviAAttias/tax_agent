package com.abco.taxassessment.domain.statement.dto;

import com.abco.taxassessment.domain.statement.domain.entity.StatementEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Request to upload a bank statement for processing")
public record StatementUploadRequest(

        @NotNull
        @Schema(description = "Bank account ID this statement belongs to", required = true)
        UUID accountId,

        @NotNull
        @Schema(description = "Statement period start date", example = "2024-01-01")
        LocalDate periodStart,

        @NotNull
        @Schema(description = "Statement period end date", example = "2024-01-31")
        LocalDate periodEnd,

        @NotNull
        @Schema(description = "Source file format", allowableValues = {"PDF", "CSV", "OFX", "QFX", "MT940"})
        StatementEntity.SourceFormat sourceFormat,

        @Schema(description = "Whether the statement covers a partial period (missing days)")
        boolean wasPartial
) {}
