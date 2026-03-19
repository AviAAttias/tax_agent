package com.abco.taxassessment.tenant;

import com.abco.taxassessment.exception.TenantContextMissingException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that establishes TenantContext from verified JWT claims (§6.2, §7).
 *
 * Tenant identity is extracted from the JWT claim 'tid' (preferred) or 'tenant_id'.
 * The JWT signature has already been verified by Spring Security's OAuth2 resource server
 * before this filter runs — we do NOT re-verify here.
 *
 * Order = 1 (first) ensures tenant context is available for all downstream filters and handlers.
 * Context is cleared in a finally block to prevent ThreadLocal leakage across requests.
 *
 * Forbidden sources of tenant identity (§6.2):
 *   - Path parameters (tenantId in URL is used for routing/authorization, not identity)
 *   - Request body or query parameters
 *   - Any unauthenticated source
 */
@Component
@Order(1)
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

    private static final String JWT_CLAIM_TID = "tid";
    private static final String JWT_CLAIM_TENANT_ID = "tenant_id";
    private static final String MDC_TENANT_ID = "tenantId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            UUID tenantId = extractTenantIdFromSecurityContext();
            if (tenantId != null) {
                TenantContext.set(tenantId);
                MDC.put(MDC_TENANT_ID, tenantId.toString());
                log.debug("Tenant context established: {}", tenantId);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(MDC_TENANT_ID);
        }
    }

    /**
     * Actuator and public endpoints (e.g., /actuator/health) are not authenticated.
     * For these, we skip tenant extraction — the downstream handler is responsible for
     * calling TenantContext.requireTenantId() if it needs a tenant.
     */
    private UUID extractTenantIdFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        String tenantIdClaim = jwt.getClaimAsString(JWT_CLAIM_TID);
        if (tenantIdClaim == null) {
            tenantIdClaim = jwt.getClaimAsString(JWT_CLAIM_TENANT_ID);
        }
        if (tenantIdClaim == null) {
            throw new TenantContextMissingException("JWT is missing required tenant claim ('tid' or 'tenant_id')");
        }
        try {
            return UUID.fromString(tenantIdClaim);
        } catch (IllegalArgumentException e) {
            throw new TenantContextMissingException("JWT tenant claim is not a valid UUID: " + tenantIdClaim);
        }
    }

    /**
     * Do not run this filter on actuator paths — they are authenticated differently
     * and do not require a tenant context.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }
}
