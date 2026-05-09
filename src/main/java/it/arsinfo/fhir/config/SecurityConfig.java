package it.arsinfo.fhir.config;

import it.arsinfo.fhir.security.jwt.KeycloakRolesConverter;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two independent filter chains:
 *
 * Chain 1 (/fhir/**, /api/admin/**) — stateless JWT resource server.
 *   Fine-grained FHIR authorization is delegated to SmartAuthorizationInterceptor.
 *
 * Chain 2 (/admin/**) — same JWT bearer validation but session-aware so Thymeleaf
 *   CSRF tokens work correctly for HTML form submissions.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final KeycloakRolesConverter rolesConverter;
    private final RbacProperties rbacProperties;

    public SecurityConfig(KeycloakRolesConverter rolesConverter, RbacProperties rbacProperties) {
        this.rolesConverter   = rolesConverter;
        this.rbacProperties   = rbacProperties;
    }

    /** Chain 1: FHIR API + REST admin API — stateless, no CSRF. */
    @Bean
    @Order(1)
    public SecurityFilterChain fhirFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/fhir/**", "/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/fhir/metadata").permitAll()
                .requestMatchers("/api/admin/**").hasAnyRole("SUPER_ADMIN", "CLINICAL_ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesConverter))
            );
        return http.build();
    }

    /** Chain 2: Admin UI — stateful session, CSRF enabled, JWT bearer auth. */
    @Bean
    @Order(2)
    public SecurityFilterChain adminUiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/admin/**", "/error/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error/**").permitAll()
                .anyRequest().hasAnyRole("SUPER_ADMIN", "CLINICAL_ADMIN")
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesConverter))
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/error/403")
            );
        return http.build();
    }

    /** Chain 3: actuator + static resources — completely open. */
    @Bean
    @Order(3)
    public SecurityFilterChain openFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/actuator/**", "/h2-console/**", "/swagger-ui/**", "/v3/api-docs/**")
            .csrf(csrf -> csrf.disable())
            .headers(h -> h.frameOptions(fo -> fo.sameOrigin())) // needed for H2 console
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * JwtDecoder configured from application.yml spring.security.oauth2.resourceserver.jwt.*
     * Uses NimbusJwtDecoder with JWKS URI — works for any OIDC/OAuth2 provider.
     * Issuer validation is automatic when issuer-uri is set.
     */
    @Bean
    public JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) {
        var jwt = properties.getJwt();
        if (jwt.getIssuerUri() != null && !jwt.getIssuerUri().isBlank()) {
            // Uses OIDC discovery to resolve JWKS URI automatically
            return JwtDecoders.fromIssuerLocation(jwt.getIssuerUri());
        }
        // Fallback: explicit JWKS URI (no issuer validation)
        return NimbusJwtDecoder.withJwkSetUri(jwt.getJwkSetUri()).build();
    }
}
