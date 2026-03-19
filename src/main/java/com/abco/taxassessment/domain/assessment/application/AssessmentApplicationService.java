package com.abco.taxassessment.domain.assessment.application;

import com.abco.taxassessment.domain.assessment.domain.entity.AnomalyEntity;
import com.abco.taxassessment.domain.assessment.domain.entity.TaxAssessmentEntity;
import com.abco.taxassessment.domain.assessment.domain.service.AssessmentService;
import com.abco.taxassessment.domain.assessment.dto.AnomalyResponse;
import com.abco.taxassessment.domain.assessment.dto.AssessmentResponse;
import com.abco.taxassessment.domain.assessment.mapper.AssessmentMapper;
import com.abco.taxassessment.domain.assessment.repository.AnomalyRepository;
import com.abco.taxassessment.domain.assessment.repository.TaxAssessmentRepository;
import com.abco.taxassessment.exception.ResourceNotFoundException;
import com.abco.taxassessment.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Assessment application service — orchestrates UC-21 through UC-30.
 * No domain logic here (§3.2). Composes domain service calls and maps responses.
 */
@Service
public class AssessmentApplicationService {

    private final AssessmentService assessmentService;
    private final TaxAssessmentRepository assessmentRepository;
    private final AnomalyRepository anomalyRepository;
    private final AssessmentMapper assessmentMapper;

    public AssessmentApplicationService(AssessmentService assessmentService,
                                         TaxAssessmentRepository assessmentRepository,
                                         AnomalyRepository anomalyRepository,
                                         AssessmentMapper assessmentMapper) {
        this.assessmentService = assessmentService;
        this.assessmentRepository = assessmentRepository;
        this.anomalyRepository = anomalyRepository;
        this.assessmentMapper = assessmentMapper;
    }

    public List<AssessmentResponse> listAssessments() {
        UUID tenantId = TenantContext.requireTenantId();
        return assessmentMapper.toResponseList(assessmentRepository.findAllByTenantId(tenantId));
    }

    public AssessmentResponse getAssessment(UUID assessmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        TaxAssessmentEntity entity = assessmentRepository
                .findByTenantIdAndId(tenantId, assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("TaxAssessment", assessmentId));
        return assessmentMapper.toResponse(entity);
    }

    public AssessmentResponse finalizeAssessment(UUID assessmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        TaxAssessmentEntity entity = assessmentService.finalizeAssessment(tenantId, assessmentId);
        return assessmentMapper.toResponse(entity);
    }

    public List<AnomalyResponse> listAnomalies(UUID assessmentId) {
        UUID tenantId = TenantContext.requireTenantId();
        List<AnomalyEntity> anomalies = anomalyRepository
                .findAllByTenantIdAndAssessmentId(tenantId, assessmentId);
        return assessmentMapper.toAnomalyResponseList(anomalies);
    }
}
