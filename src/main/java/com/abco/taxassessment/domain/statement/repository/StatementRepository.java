package com.abco.taxassessment.domain.statement.repository;

import com.abco.taxassessment.domain.statement.domain.entity.StatementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped repository for StatementEntity (§8 Layer 4). */
@Repository
public interface StatementRepository extends JpaRepository<StatementEntity, UUID> {

    Optional<StatementEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<StatementEntity> findAllByTenantId(UUID tenantId);

    List<StatementEntity> findAllByTenantIdAndAccountId(UUID tenantId, UUID accountId);

    /**
     * Duplicate detection: find statements with overlapping periods for the same account.
     * Used by ingestion to detect re-uploads before quarantine decision.
     */
    @Query("SELECT s FROM StatementEntity s WHERE s.tenantId = :tenantId " +
           "AND s.accountId = :accountId " +
           "AND s.periodStart <= :periodEnd AND s.periodEnd >= :periodStart " +
           "AND s.status != 'DUPLICATE_QUARANTINED'")
    List<StatementEntity> findOverlappingStatements(@Param("tenantId") UUID tenantId,
                                                     @Param("accountId") UUID accountId,
                                                     @Param("periodStart") LocalDate periodStart,
                                                     @Param("periodEnd") LocalDate periodEnd);

    /**
     * File hash lookup for exact duplicate detection (UC-09).
     * Covered by: tenant_id + file_hash index.
     */
    Optional<StatementEntity> findByTenantIdAndFileHash(UUID tenantId, String fileHash);

    List<StatementEntity> findAllByTenantIdAndStatusIn(UUID tenantId,
                                                        List<StatementEntity.StatementStatus> statuses);
}
