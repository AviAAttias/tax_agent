package com.abco.taxassessment.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Sets the PostgreSQL session variable app.tenant_id on every connection acquired
 * from the pool, enabling Row Level Security (RLS) enforcement at the DB engine level (§8 Layer 2).
 *
 * Called by DataSourceConfig's connection preparer wrapper.
 * Uses SET LOCAL so the variable is scoped to the current transaction, which is
 * compatible with PgBouncer session mode (§15.3).
 *
 * Security invariant: if TenantContext is not set, the SET LOCAL is skipped.
 * RLS at the DB layer will then reject all queries on tenant-owned tables
 * because app.tenant_id will not match any row's tenant_id.
 */
@Component
public class TenantConnectionPreparer {

    private static final Logger log = LoggerFactory.getLogger(TenantConnectionPreparer.class);

    private static final String SET_TENANT_SQL = "SET LOCAL app.tenant_id = ?";

    public void prepareConnection(Connection connection) throws SQLException {
        UUID tenantId = TenantContext.get();
        if (tenantId == null) {
            // Allowed for non-tenant paths (Liquibase migrations, health checks).
            // RLS blocks unauthorized access at DB level regardless.
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(SET_TENANT_SQL)) {
            stmt.setString(1, tenantId.toString());
            stmt.execute();
            log.debug("Set DB session app.tenant_id = {}", tenantId);
        }
    }
}
