package com.abco.taxassessment.domain.agent.repository;

import com.abco.taxassessment.domain.agent.domain.entity.AgentJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped repository for AgentJobEntity (§8 Layer 4). */
@Repository
public interface AgentJobRepository extends JpaRepository<AgentJobEntity, UUID> {

    Optional<AgentJobEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<AgentJobEntity> findAllByTenantIdAndStatementId(UUID tenantId, UUID statementId);

    Optional<AgentJobEntity> findByTenantIdAndStatementIdAndAgentName(
            UUID tenantId,
            UUID statementId,
            AgentJobEntity.AgentName agentName);

    List<AgentJobEntity> findAllByStatus(AgentJobEntity.AgentJobStatus status);

    long countByStatus(AgentJobEntity.AgentJobStatus status);
}
