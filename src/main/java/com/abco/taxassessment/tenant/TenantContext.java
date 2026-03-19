package com.abco.taxassessment.tenant;

import com.abco.taxassessment.exception.TenantContextMissingException;

import java.util.Objects;
import java.util.UUID;

/**
 * Request-scoped, ThreadLocal-backed tenant identity store (§7).
 *
 * Rules:
 *  - set() is called exactly once per request by TenantFilter (HTTP) or agent consumer setup.
 *  - clear() is called in a finally block by the same component that called set().
 *  - requireTenantId() fails fast — missing context is a programming error or auth violation.
 *  - Tenant ID originates exclusively from verified JWT/API-Key/mTLS (§6.2). Never from user input.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();

    private TenantContext() {
        throw new UnsupportedOperationException("TenantContext is a static utility class");
    }

    public static void set(UUID tenantId) {
        TENANT.set(Objects.requireNonNull(tenantId, "tenantId must not be null"));
    }

    public static UUID get() {
        return TENANT.get();
    }

    /**
     * Returns the current tenant ID or throws {@link TenantContextMissingException} (→ HTTP 401).
     * Use this in all domain service entry points per §8 Layer 5.
     */
    public static UUID requireTenantId() {
        UUID id = TENANT.get();
        if (id == null) {
            throw new TenantContextMissingException("Tenant context not established for this thread");
        }
        return id;
    }

    public static void clear() {
        TENANT.remove();
    }
}
