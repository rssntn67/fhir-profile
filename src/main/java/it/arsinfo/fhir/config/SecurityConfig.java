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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import java.util.*;

/**
 * Three independent filter chains:
 *
 * Chain 1 (/fhir/**, /api/**) — stateless JWT resource server.
 *   Fine-grained FHIR authorization is delegated to SmartAuthorizationInterceptor.
 *
 * Chain 2 (/admin/**, /login/**, /) — OAuth2 Authorization Code login.
 *   Browser redirects to Keycloak login page; session stores the authenticated user.
 *
 * Chain 3 (actuator, swagger, h2-console) — completely open.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final KeycloakRolesConverter rolesConverter;
    private final ClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(KeycloakRolesConverter rolesConverter,
                          ClientRegistrationRepository clientRegistrationRepository) {
        this.rolesConverter = rolesConverter;
        this.clientRegistrationRepository = clientRegistrationRepository;
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

    /**
     * Chain 2: Admin UI — OAuth2 Authorization Code login via Keycloak.
     * Unauthenticated requests are redirected to Keycloak's login page.
     * After login, Keycloak redirects back and a session is established.
     * Realm roles from the ID token are mapped to ROLE_X GrantedAuthority.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain adminUiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(new OrRequestMatcher(
                new AntPathRequestMatcher("/admin/**"),
                new AntPathRequestMatcher("/login/**"),
                new AntPathRequestMatcher("/logout"),
                new AntPathRequestMatcher("/oauth2/**"),
                new AntPathRequestMatcher("/error/**"),
                new AntPathRequestMatcher("/")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/error/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/login/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/oauth2/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/")).permitAll()
                .anyRequest().hasAnyRole("SUPER_ADMIN", "CLINICAL_ADMIN")
            )
            // Spring Security 6 lazy-loads the CSRF token by default; Thymeleaf's th:action
            // needs it eagerly bound to the request attribute so the hidden _csrf field is
            // injected into the logout form (and any other POST form in the admin UI).
            .csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .oauth2Login(login -> login
                .defaultSuccessUrl("/admin/roles", true)
                .userInfoEndpoint(userInfo -> userInfo
                    .userAuthoritiesMapper(keycloakAuthoritiesMapper()))
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler(oidcLogoutSuccessHandler())
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .exceptionHandling(ex -> ex
                .accessDeniedHandler((request, response, denied) ->
                        response.sendError(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN))
            );
        return http.build();
    }

    /**
     * OIDC RP-Initiated Logout: after invalidating the local session, redirect the
     * browser to Keycloak's end-session endpoint so the SSO session is also terminated.
     * Without this, Keycloak re-authenticates the user silently on the next /admin request.
     */
    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
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
     * Maps Keycloak realm roles from the OIDC ID token (realm_access.roles) to
     * Spring Security ROLE_X GrantedAuthority objects for the admin UI session.
     */
    @Bean
    public GrantedAuthoritiesMapper keycloakAuthoritiesMapper() {
        return authorities -> {
            Set<GrantedAuthority> mapped = new HashSet<>(authorities);
            for (GrantedAuthority authority : authorities) {
                Map<String, Object> claims = null;
                if (authority instanceof OidcUserAuthority oidc) {
                    claims = oidc.getIdToken().getClaims();
                } else if (authority instanceof OAuth2UserAuthority oauth2) {
                    claims = oauth2.getAttributes();
                }
                if (claims != null) {
                    extractRealmRoles(claims).forEach(mapped::add);
                }
            }
            return mapped;
        };
    }

    @SuppressWarnings("unchecked")
    private Set<SimpleGrantedAuthority> extractRealmRoles(Map<String, Object> claims) {
        Object realmAccess = claims.get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> ra)) return Set.of();
        Object roles = ra.get("roles");
        if (!(roles instanceof List<?> roleList)) return Set.of();
        Set<SimpleGrantedAuthority> result = new HashSet<>();
        for (Object role : roleList) {
            result.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return result;
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
