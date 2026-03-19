package com.abco.taxassessment.domain.notification.repository;

import com.abco.taxassessment.domain.notification.domain.entity.NotificationEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped repository for NotificationEventEntity (§8 Layer 4). */
@Repository
public interface NotificationEventRepository extends JpaRepository<NotificationEventEntity, UUID> {

    Optional<NotificationEventEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<NotificationEventEntity> findByIdempotencyKey(String idempotencyKey);

    List<NotificationEventEntity> findAllByTenantId(UUID tenantId);

    List<NotificationEventEntity> findAllByTenantIdAndStatus(
            UUID tenantId, NotificationEventEntity.NotificationStatus status);

    /** For digest aggregation: find pending events in time window */
    List<NotificationEventEntity> findAllByTenantIdAndStatusAndDigestGroupIdIsNull(
            UUID tenantId, NotificationEventEntity.NotificationStatus status);

    List<NotificationEventEntity> findAllByTenantIdAndDigestGroupId(UUID tenantId, UUID digestGroupId);

    /** For retry processing: find failed notifications created within window */
    List<NotificationEventEntity> findAllByStatusAndAttemptCountLessThanAndCreatedAtAfter(
            NotificationEventEntity.NotificationStatus status,
            int maxAttempts,
            Instant createdAfter);
}
