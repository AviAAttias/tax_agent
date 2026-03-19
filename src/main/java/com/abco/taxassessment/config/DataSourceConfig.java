package com.abco.taxassessment.config;

import com.abco.taxassessment.tenant.TenantConnectionPreparer;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DataSource configuration implementing the connection preparer pattern for RLS (§8 Layer 2).
 *
 * Architecture:
 *   LazyConnectionDataSourceProxy wraps HikariCP.
 *   When a connection is actually acquired (lazily), TenantConnectionPreparer sets
 *   the PostgreSQL session variable app.tenant_id for RLS enforcement.
 *
 * PgBouncer sidecar note (§15.3):
 *   The JDBC URL points to localhost:5432 — the PgBouncer sidecar.
 *   PgBouncer is configured in session mode to support SET LOCAL for RLS.
 *   prepareThreshold=0 disables named prepared statements for PgBouncer compatibility.
 */
@Configuration
public class DataSourceConfig {

    private final TenantConnectionPreparer tenantConnectionPreparer;

    public DataSourceConfig(TenantConnectionPreparer tenantConnectionPreparer) {
        this.tenantConnectionPreparer = tenantConnectionPreparer;
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource hikariDataSource = properties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        // Wrap with a proxy that calls TenantConnectionPreparer on each connection acquisition
        return new TenantAwareDataSourceProxy(hikariDataSource, tenantConnectionPreparer);
    }

    /**
     * Extends LazyConnectionDataSourceProxy to inject tenant context into each connection.
     * LazyConnectionDataSourceProxy defers connection acquisition until the first actual
     * DB operation — important for read-only transactions and conditional data access.
     */
    static class TenantAwareDataSourceProxy extends LazyConnectionDataSourceProxy {

        private final TenantConnectionPreparer preparer;

        TenantAwareDataSourceProxy(DataSource targetDataSource, TenantConnectionPreparer preparer) {
            super(targetDataSource);
            this.preparer = preparer;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            preparer.prepareConnection(connection);
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            Connection connection = super.getConnection(username, password);
            preparer.prepareConnection(connection);
            return connection;
        }
    }
}
