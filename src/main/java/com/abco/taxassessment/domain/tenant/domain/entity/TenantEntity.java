package com.abco.taxassessment.domain.tenant.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.EntityListeners;
import java.time.Instant;
import java.util.UUID;

/**
 * Tenant entity — the root aggregate for a business client.
 *
 * NOTE: @Data is forbidden on entities (§3.2) — breaks JPA proxy equality.
 * Using @Getter, @Builder, @NoArgsConstructor, @AllArgsConstructor explicitly.
 */
@Entity
@Table(name = "tenants")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private TenantStatus status;

    @Column(name = "tax_profile_id")
    private UUID taxProfileId;

    @Column(name = "api_key_hash", length = 255)
    private String apiKeyHash;

    @Column(name = "notification_email", length = 255)
    private String notificationEmail;

    @Column(name = "notification_phone", length = 50)
    private String notificationPhone;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum TenantStatus {
        ACTIVE, SUSPENDED, PENDING_VERIFICATION, TERMINATED
    }

    // Domain behavior: tenants can be suspended
    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
    }

    public void activate() {
        this.status = TenantStatus.ACTIVE;
    }

    public boolean isActive() {
        return TenantStatus.ACTIVE == this.status;
    }
}
