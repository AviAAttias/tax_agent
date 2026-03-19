package com.abco.taxassessment.domain.assessment.application;

import com.abco.taxassessment.domain.assessment.domain.service.AssessmentService;
import com.abco.taxassessment.domain.assessment.dto.AnomalyResponse;
import com.abco.taxassessment.domain.assessment.dto.AssessmentResponse;
import com.abco.taxassessment.domain.assessment.mapper.AssessmentMapper;
import com.abco.taxassessment.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Assessment application service — orchestrates UC-21 through UC-30.
 * No domain logic here (§3.2). Composes domain service calls and maps responses.
 * Repository access is delegated to AssessmentService per the layer rules (§3.2).
 */
@Service
public class AssessmentApplicationService {

    private final AssessmentService assessmentService;
    private final AssessmentMapper assessmentMapper;

    public AssessmentApplicationService(AssessmentService assessmentService,
                                         AssessmentMapper assessmentMapper) {
        this.assessmentService = assessmentService;
        this.assessmentMapper = assessmentMapper;
    }

    public List<AssessmentResponse> listAssessments() {
        return assessmentMapper.toResponseList(assessmentService.listAssessments());
    }

    public AssessmentResponse getAssessment(UUID assessmentId) {
        return assessmentMapper.toResponse(assessmentService.getAssessment(assessmentId));
    }

    public AssessmentResponse finalizeAssessment(UUID assessmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        return assessmentMapper.toResponse(assessmentService.finalizeAssessment(tenantId, assessmentId));
    }

    public List<AnomalyResponse> listAnomalies(UUID assessmentId) {
        return assessmentMapper.toAnomalyResponseList(assessmentService.listAnomalies(assessmentId));
    }
}
