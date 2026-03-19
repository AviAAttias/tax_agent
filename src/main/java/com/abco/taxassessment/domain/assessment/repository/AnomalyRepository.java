package com.abco.taxassessment.domain.assessment.repository;

import com.abco.taxassessment.domain.assessment.domain.entity.AnomalyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped repository for AnomalyEntity (§8 Layer 4). */
@Repository
public interface AnomalyRepository extends JpaRepository<AnomalyEntity, UUID> {

    Optional<AnomalyEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AnomalyEntity> findAllByTenantId(UUID tenantId);

    List<AnomalyEntity> findAllByTenantIdAndStatus(UUID tenantId, AnomalyEntity.AnomalyStatus status);

    List<AnomalyEntity> findAllByTenantIdAndSeverityAndNotificationSentFalse(
            UUID tenantId, AnomalyEntity.AnomalySeverity severity);

    List<AnomalyEntity> findAllByTenantIdAndAssessmentId(UUID tenantId, UUID assessmentId);
}
