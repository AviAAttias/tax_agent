package com.abco.taxassessment.domain.assessment.controller;

import com.abco.taxassessment.domain.assessment.application.AssessmentApplicationService;
import com.abco.taxassessment.domain.assessment.dto.AssessmentResponse;
import com.abco.taxassessment.domain.assessment.dto.AnomalyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tax assessment endpoints (UC-21 through UC-24, UC-25 through UC-30).
 * Zero business logic — delegates to AssessmentApplicationService.
 */
@RestController
@RequestMapping("/api/v1/assessments")
@Tag(name = "Assessments", description = "Tax assessment lifecycle and reporting")
@SecurityRequirement(name = "BearerAuth")
public class AssessmentController {

    private final AssessmentApplicationService assessmentApplicationService;

    public AssessmentController(AssessmentApplicationService assessmentApplicationService) {
        this.assessmentApplicationService = assessmentApplicationService;
    }

    @GetMapping
    @Operation(summary = "List all assessments for the current tenant")
    @PreAuthorize("hasAuthority('SCOPE_assessment:read')")
    public ResponseEntity<List<AssessmentResponse>> listAssessments() {
        return ResponseEntity.ok(assessmentApplicationService.listAssessments());
    }

    @GetMapping("/{assessmentId}")
    @Operation(
        summary = "Get a specific assessment",
        responses = {
            @ApiResponse(responseCode = "200", description = "Assessment found"),
            @ApiResponse(responseCode = "404", description = "Assessment not found")
        }
    )
    @PreAuthorize("hasAuthority('SCOPE_assessment:read')")
    public ResponseEntity<AssessmentResponse> getAssessment(@PathVariable UUID assessmentId) {
        return ResponseEntity.ok(assessmentApplicationService.getAssessment(assessmentId));
    }

    @PostMapping("/{assessmentId}/finalize")
    @Operation(
        summary = "Finalize an assessment (UC-24)",
        description = "Produces the immutable, timestamped, signed assessment artifact. " +
                      "Only assessments in DRAFT or UNDER_REVIEW status can be finalized."
    )
    @PreAuthorize("hasAuthority('SCOPE_assessment:write') and hasRole('ACCOUNTANT')")
    public ResponseEntity<AssessmentResponse> finalizeAssessment(@PathVariable UUID assessmentId) {
        return ResponseEntity.ok(assessmentApplicationService.finalizeAssessment(assessmentId));
    }

    @GetMapping("/{assessmentId}/anomalies")
    @Operation(summary = "List anomalies for an assessment (UC-22)")
    @PreAuthorize("hasAuthority('SCOPE_assessment:read')")
    public ResponseEntity<List<AnomalyResponse>> listAnomalies(@PathVariable UUID assessmentId) {
        return ResponseEntity.ok(assessmentApplicationService.listAnomalies(assessmentId));
    }
}
