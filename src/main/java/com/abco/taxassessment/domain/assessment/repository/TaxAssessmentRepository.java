package com.abco.taxassessment.domain.assessment.repository;

import com.abco.taxassessment.domain.assessment.domain.entity.TaxAssessmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped repository for TaxAssessmentEntity (§8 Layer 4). */
@Repository
public interface TaxAssessmentRepository extends JpaRepository<TaxAssessmentEntity, UUID> {

    Optional<TaxAssessmentEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<TaxAssessmentEntity> findAllByTenantId(UUID tenantId);

    Optional<TaxAssessmentEntity> findByTenantIdAndStatementIdAndStatus(
            UUID tenantId,
            UUID statementId,
            TaxAssessmentEntity.AssessmentStatus status);

    List<TaxAssessmentEntity> findAllByTenantIdAndStatus(
            UUID tenantId,
            TaxAssessmentEntity.AssessmentStatus status);

    List<TaxAssessmentEntity> findAllByTenantIdAndPeriodStartGreaterThanEqualAndPeriodEndLessThanEqual(
            UUID tenantId,
            LocalDate periodStart,
            LocalDate periodEnd);
}
