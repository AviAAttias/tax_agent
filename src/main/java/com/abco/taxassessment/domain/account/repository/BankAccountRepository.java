package com.abco.taxassessment.domain.account.repository;

import com.abco.taxassessment.domain.account.domain.entity.BankAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for BankAccountEntity (§8 Layer 4).
 *
 * All methods are tenant-scoped by method signature.
 * Unscoped findById and findAll are forbidden on tenant-owned entities.
 * RLS at the DB layer and Hibernate filters provide defense-in-depth (Layers 2 and 3).
 */
@Repository
public interface BankAccountRepository extends JpaRepository<BankAccountEntity, UUID> {

    Optional<BankAccountEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<BankAccountEntity> findAllByTenantId(UUID tenantId);

    List<BankAccountEntity> findAllByTenantIdAndActiveTrue(UUID tenantId);

    boolean existsByTenantIdAndId(UUID tenantId, UUID id);
}
