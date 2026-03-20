package com.abco.taxassessment.domain.account.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Bank account linked by a tenant (UC-03).
 * Tenant-owned entity: tenantId is non-nullable, present in all queries (§3.2, §8).
 * Hibernate filter provides ORM-level defense-in-depth isolation (§8 Layer 3).
 */
@Entity
@Table(name = "bank_accounts")
@EntityListeners(AuditingEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "institution", nullable = false, length = 255)
    private String institution;

    @Column(name = "account_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "alias", length = 255)
    private String alias;

    /** Plaid access token — stored encrypted; raw value never returned in API responses. */
    @Column(name = "plaid_access_token_encrypted", length = 512)
    private String plaidAccessTokenEncrypted;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "last_fetched_at")
    private Instant lastFetchedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum AccountType {
        CHECKING, SAVINGS, CREDIT, LINE_OF_CREDIT, INVESTMENT
    }

    public void recordFetch(Instant fetchedAt) {
        this.lastFetchedAt = fetchedAt;
    }

    public void deactivate() {
        this.active = false;
    }
}
