package com.abco.taxassessment.event.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Fetch unpublished events ordered by occurredAt for the poller.
     * Limit is applied in the service layer via Pageable.
     */
    @Query("SELECT o FROM OutboxEventEntity o WHERE o.published = false ORDER BY o.occurredAt ASC")
    List<OutboxEventEntity> findUnpublishedEvents(org.springframework.data.domain.Pageable pageable);

    boolean existsByEventId(String eventId);
}
