package com.abco.taxassessment.domain.tenant.repository;

import com.abco.taxassessment.domain.tenant.domain.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * TenantEntity repository.
 *
 * Tenants are not row-level-security scoped (they ARE the top-level scope),
 * but all writes are protected by platform-admin role checks at the service layer.
 */
@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, UUID> {

    Optional<TenantEntity> findByApiKeyHash(String apiKeyHash);

    boolean existsByName(String name);
}
