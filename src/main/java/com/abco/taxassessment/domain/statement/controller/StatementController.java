package com.abco.taxassessment.domain.statement.controller;

import com.abco.taxassessment.domain.statement.application.StatementApplicationService;
import com.abco.taxassessment.domain.statement.dto.StatementResponse;
import com.abco.taxassessment.domain.statement.dto.StatementUploadRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Statement management endpoints (UC-07, UC-12, UC-13).
 *
 * Controller responsibilities (§3.2):
 * - HTTP method mapping and request validation
 * - Multipart file extraction
 * - Delegation to StatementApplicationService
 * - Response mapping and HTTP status
 * - OpenAPI annotations
 *
 * Zero business logic in this class.
 * Tenant identity comes from JWT (established by TenantFilter) — not from path params.
 */
@RestController
@RequestMapping("/api/v1/statements")
@Tag(name = "Statements", description = "Bank statement ingestion and management")
@SecurityRequirement(name = "BearerAuth")
public class StatementController {

    private final StatementApplicationService statementApplicationService;

    public StatementController(StatementApplicationService statementApplicationService) {
        this.statementApplicationService = statementApplicationService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload a bank statement",
        description = "UC-07: Upload a PDF, CSV, OFX, QFX, or MT940 statement for processing. " +
                      "Duplicate detection runs automatically. Tenant identity from JWT.",
        responses = {
            @ApiResponse(responseCode = "202", description = "Statement accepted for processing"),
            @ApiResponse(responseCode = "409", description = "Duplicate statement — quarantined",
                    content = @Content(mediaType = "application/problem+json")),
            @ApiResponse(responseCode = "400", description = "Validation error")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_statement:write')")
    public ResponseEntity<StatementResponse> uploadStatement(
            @RequestPart("metadata") @Valid StatementUploadRequest request,
            @RequestPart("file") @Parameter(description = "Statement file") MultipartFile file
    ) throws IOException {
        StatementResponse response = statementApplicationService.uploadStatement(request, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping(value = "/{statementId}/amendments",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload a statement amendment",
        description = "UC-12: Re-upload a corrected statement. Archives the prior assessment " +
                      "and reprocesses. Amendment notification sent to tenant.",
        responses = {
            @ApiResponse(responseCode = "202", description = "Amendment accepted for processing"),
            @ApiResponse(responseCode = "404", description = "Original statement not found")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_statement:write')")
    public ResponseEntity<StatementResponse> uploadAmendment(
            @PathVariable UUID statementId,
            @RequestPart("metadata") @Valid StatementUploadRequest request,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        StatementResponse response = statementApplicationService.uploadAmendment(statementId, request, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    @Operation(
        summary = "List all statements for the current tenant",
        description = "Returns all statements (any status). Tenant scoped by JWT.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Statement list")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_statement:read')")
    public ResponseEntity<List<StatementResponse>> listStatements() {
        return ResponseEntity.ok(statementApplicationService.listStatements());
    }

    @GetMapping("/{statementId}")
    @Operation(
        summary = "Get a statement by ID",
        responses = {
            @ApiResponse(responseCode = "200", description = "Statement found"),
            @ApiResponse(responseCode = "404", description = "Statement not found")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_statement:read')")
    public ResponseEntity<StatementResponse> getStatement(@PathVariable UUID statementId) {
        return ResponseEntity.ok(statementApplicationService.getStatement(statementId));
    }
}
