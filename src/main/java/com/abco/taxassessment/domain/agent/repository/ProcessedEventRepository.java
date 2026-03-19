package com.abco.taxassessment.domain.agent.repository;

import com.abco.taxassessment.domain.agent.domain.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {

    boolean existsByEventIdAndConsumerGroup(String eventId, String consumerGroup);
}
