package it.arsinfo.fhir.security.jwt;

import it.arsinfo.fhir.config.RbacProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class JwtClaimsExtractorTest {

    private JwtClaimsExtractor extractor;
    private RbacProperties props;

    @BeforeEach
    void setUp() {
        props = new RbacProperties(); // defaults: realm_access.roles, scope, patient
        extractor = new JwtClaimsExtractor(props);
    }

    // ── extractRoleNames ────────────────────────────────────────────────────

    @Test
    void extractRoleNames_keycloakRealmAccessRoles_returnsRoleList() {
        Jwt jwt = buildJwt(Map.of(
            "realm_access", Map.of("roles", List.of("PRACTITIONER", "READONLY"))
        ));
        assertThat(extractor.extractRoleNames(jwt))
                .containsExactlyInAnyOrder("PRACTITIONER", "READONLY");
    }

    @Test
    void extractRoleNames_flatRolesClaim_returnsRoles() {
        props.setRolesClaim("roles");
        Jwt jwt = buildJwt(Map.of("roles", List.of("NURSE")));
        assertThat(extractor.extractRoleNames(jwt)).containsExactly("NURSE");
    }

    @Test
    void extractRoleNames_missingClaim_returnsEmptyList() {
        Jwt jwt = buildJwt(Map.of());
        assertThat(extractor.extractRoleNames(jwt)).isEmpty();
    }

    @Test
    void extractRoleNames_threeLevel_keycloakClientRoles() {
        props.setRolesClaim("resource_access.fhir-server.roles");
        Jwt jwt = buildJwt(Map.of(
            "resource_access", Map.of("fhir-server", Map.of("roles", List.of("SUPER_ADMIN")))
        ));
        assertThat(extractor.extractRoleNames(jwt)).containsExactly("SUPER_ADMIN");
    }

    // ── extractPatientId ────────────────────────────────────────────────────

    @Test
    void extractPatientId_primaryClaim_returnsId() {
        Jwt jwt = buildJwt(Map.of("patient", "abc123"));
        Optional<String> id = extractor.extractPatientId(jwt);
        assertThat(id).isPresent().contains("abc123");
    }

    @Test
    void extractPatientId_fallbackClaim_returnsId() {
        Jwt jwt = buildJwt(Map.of(
            "launch_context", Map.of("patient", "xyz789")
        ));
        Optional<String> id = extractor.extractPatientId(jwt);
        assertThat(id).isPresent().contains("xyz789");
    }

    @Test
    void extractPatientId_noClaim_returnsEmpty() {
        Jwt jwt = buildJwt(Map.of());
        assertThat(extractor.extractPatientId(jwt)).isEmpty();
    }

    // ── resolvePath ─────────────────────────────────────────────────────────

    @Test
    void resolvePath_singleKey_resolves() {
        Map<String, Object> claims = Map.of("scope", "patient/Patient.read");
        assertThat(extractor.resolvePath(claims, "scope")).isEqualTo("patient/Patient.read");
    }

    @Test
    void resolvePath_missingKey_returnsNull() {
        assertThat(extractor.resolvePath(Map.of(), "foo.bar")).isNull();
    }

    @Test
    void resolvePath_nullPath_returnsNull() {
        assertThat(extractor.resolvePath(Map.of("x", "y"), null)).isNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Jwt buildJwt(Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("user-sub-123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claims(c -> c.putAll(extraClaims))
                .build();
    }
}
