package com.abco.taxassessment.domain.notification.repository;

import com.abco.taxassessment.domain.notification.domain.entity.NotificationEventEntity;
import com.abco.taxassessment.domain.notification.domain.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped repository for NotificationPreferenceEntity (§8 Layer 4). */
@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, UUID> {

    List<NotificationPreferenceEntity> findAllByTenantId(UUID tenantId);

    List<NotificationPreferenceEntity> findAllByTenantIdAndEventTypeAndEnabledTrue(
            UUID tenantId,
            NotificationEventEntity.NotificationEventType eventType);

    Optional<NotificationPreferenceEntity> findByTenantIdAndEventTypeAndChannel(
            UUID tenantId,
            NotificationEventEntity.NotificationEventType eventType,
            NotificationEventEntity.NotificationChannel channel);
}
