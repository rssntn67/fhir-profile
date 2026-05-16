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
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

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

    /**
     * Chain 1: FHIR API + REST admin API — stateless, no CSRF.
     *
     * Uses AntPathRequestMatcher because the HAPI FHIR servlet is a plain
     * jakarta.servlet.HttpServlet (not a Spring MVC controller). Spring Security 6
     * defaults to MvcRequestMatcher which only matches paths routed through
     * DispatcherServlet — it would never match /fhir/** and the BearerToken
     * filter would silently skip all FHIR requests.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain fhirFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(new OrRequestMatcher(
                new AntPathRequestMatcher("/fhir/**"),
                new AntPathRequestMatcher("/api/**")))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/fhir/metadata")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/admin/**")).hasAnyRole("SUPER_ADMIN", "CLINICAL_ADMIN")
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
            .securityMatcher(new OrRequestMatcher(
                new AntPathRequestMatcher("/admin/**"),
                new AntPathRequestMatcher("/error/**")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/error/**")).permitAll()
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
            .securityMatcher(new OrRequestMatcher(
                new AntPathRequestMatcher("/actuator/**"),
                new AntPathRequestMatcher("/h2-console/**"),
                new AntPathRequestMatcher("/swagger-ui/**"),
                new AntPathRequestMatcher("/v3/api-docs/**")))
            .csrf(csrf -> csrf.disable())
            .headers(h -> h.frameOptions(fo -> fo.sameOrigin()))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * JwtDecoder built from jwk-set-uri (lazy key fetch — no startup call to the IdP).
     * Issuer validation is wired separately so the server starts even when Keycloak is down.
     */
    @Bean
    public JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) {
        var jwt = properties.getJwt();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwt.getJwkSetUri()).build();
        if (jwt.getIssuerUri() != null && !jwt.getIssuerUri().isBlank()) {
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwt.getIssuerUri()));
        }
        return decoder;
    }
}
