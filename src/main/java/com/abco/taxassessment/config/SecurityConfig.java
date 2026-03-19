package com.abco.taxassessment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration — enabled globally per §12.
 *
 * Rationale for each decision:
 *
 * 1. JWT RS256 is the auth mechanism for external API clients (§12).
 *    The issuer URI is injected via Vault CSI at runtime; the public key is fetched
 *    from the JWKS endpoint and cached. No secrets live in application config.
 *
 * 2. Session management is STATELESS — JWTs are self-contained; no server-side session.
 *
 * 3. CSRF is disabled — REST APIs consumed by non-browser clients; JWT authentication
 *    already prevents CSRF by requiring the Authorization header.
 *
 * 4. Actuator endpoints (health, prometheus) are permitted without authentication
 *    because they are protected at the network level by Kubernetes NetworkPolicy —
 *    they are never reachable from outside the cluster. This is documented here
 *    to satisfy the "undocumented SecurityFilterChain beans fail code review" rule (§12).
 *
 * 5. All other endpoints require authentication — no permitAll() for business routes.
 *
 * 6. Method-level security (@PreAuthorize) is enabled for RBAC within tenant (§12).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Actuator probes — protected by K8s NetworkPolicy at the network layer
                .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info")
                    .permitAll()
                // OpenAPI docs — accessible within cluster; remove in production if not needed
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                // All API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> {}));

        return http.build();
    }
}
