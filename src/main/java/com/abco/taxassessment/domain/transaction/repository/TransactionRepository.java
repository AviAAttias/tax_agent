package com.abco.taxassessment.domain.transaction.repository;

import com.abco.taxassessment.domain.transaction.domain.entity.TransactionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tenant-scoped repository for TransactionEntity (§8 Layer 4). */
@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    Optional<TransactionEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<TransactionEntity> findAllByTenantIdAndStatementId(UUID tenantId, UUID statementId);

    List<TransactionEntity> findAllByTenantIdAndTransactionDateBetween(UUID tenantId,
                                                                        LocalDate start,
                                                                        LocalDate end);

    List<TransactionEntity> findAllByTenantIdAndCategoryCode(UUID tenantId, String categoryCode);

    List<TransactionEntity> findAllByTenantIdAndFlaggedForReviewTrue(UUID tenantId);

    /**
     * Interaccount transfer detection (UC-20): find matching credit for a debit
     * within a time window (±3 days) and matching amount.
     *
     * Native SQL permitted because:
     * (a) JPQL cannot express the date arithmetic efficiently without N+1 risk
     * (b) Covered by integration test TransactionRepositoryIntegrationTest
     * (c) Deviation documented here per §3.2
     */
    @Query(value = "SELECT * FROM transactions " +
                   "WHERE tenant_id = :tenantId " +
                   "AND amount = :amount " +
                   "AND sign = :#{#sign.name()} " +
                   "AND transaction_date BETWEEN :startDate AND :endDate " +
                   "AND id != :excludeId " +
                   "AND is_interaccount_transfer = false " +
                   "LIMIT 5",
           nativeQuery = true)
    List<TransactionEntity> findPotentialTransferMatch(
            @Param("tenantId") UUID tenantId,
            @Param("amount") BigDecimal amount,
            @Param("sign") TransactionEntity.TransactionSign sign,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") UUID excludeId);

    long countByTenantIdAndStatementId(UUID tenantId, UUID statementId);
}
