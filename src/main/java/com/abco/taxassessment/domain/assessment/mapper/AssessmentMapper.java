package com.abco.taxassessment.domain.assessment.mapper;

import com.abco.taxassessment.domain.assessment.domain.entity.AnomalyEntity;
import com.abco.taxassessment.domain.assessment.domain.entity.TaxAssessmentEntity;
import com.abco.taxassessment.domain.assessment.dto.AnomalyResponse;
import com.abco.taxassessment.domain.assessment.dto.AssessmentResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface AssessmentMapper {

    AssessmentResponse toResponse(TaxAssessmentEntity entity);

    List<AssessmentResponse> toResponseList(List<TaxAssessmentEntity> entities);

    AnomalyResponse toAnomalyResponse(AnomalyEntity entity);

    List<AnomalyResponse> toAnomalyResponseList(List<AnomalyEntity> entities);
}
