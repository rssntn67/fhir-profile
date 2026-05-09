package it.arsinfo.fhir.security.jwt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class SmartScopeParserTest {

    private final SmartScopeParser parser = new SmartScopeParser();

    // ── Happy paths ───────────────────────────────────────────────────────────

    @Test
    void parse_legacyRead_producesBothReadAndSearch() {
        Optional<SmartScope> scope = parser.parseToken("patient/Patient.read");
        assertThat(scope).isPresent();
        assertThat(scope.get().context()).isEqualTo(SmartContext.PATIENT);
        assertThat(scope.get().resourceType()).isEqualTo("Patient");
        assertThat(scope.get().permissions())
                .containsExactlyInAnyOrder(SmartPermission.READ, SmartPermission.SEARCH);
    }

    @Test
    void parse_legacyWrite_producesCreateAndUpdate() {
        Optional<SmartScope> scope = parser.parseToken("user/Observation.write");
        assertThat(scope).isPresent();
        assertThat(scope.get().permissions())
                .containsExactlyInAnyOrder(SmartPermission.CREATE, SmartPermission.UPDATE);
    }

    @Test
    void parse_v2Cruds_producesAllPermissions() {
        Optional<SmartScope> scope = parser.parseToken("system/*.cruds");
        assertThat(scope).isPresent();
        assertThat(scope.get().context()).isEqualTo(SmartContext.SYSTEM);
        assertThat(scope.get().resourceType()).isEqualTo("*");
        assertThat(scope.get().permissions()).hasSize(5);
    }

    @Test
    void parse_v2PartialPerms_producesCorrectSubset() {
        Optional<SmartScope> scope = parser.parseToken("user/Condition.ru");
        assertThat(scope).isPresent();
        assertThat(scope.get().permissions())
                .containsExactlyInAnyOrder(SmartPermission.READ, SmartPermission.UPDATE);
    }

    @Test
    void parse_wildcardAsterisk_producesAllPerms() {
        Optional<SmartScope> scope = parser.parseToken("user/Patient.*");
        assertThat(scope).isPresent();
        assertThat(scope.get().permissions()).hasSize(5);
    }

    @Test
    void parse_spaceSeperatedString_returnsMultipleScopes() {
        List<SmartScope> scopes = parser.parse("patient/Patient.read user/Observation.write openid profile");
        assertThat(scopes).hasSize(2);
        assertThat(scopes.get(0).context()).isEqualTo(SmartContext.PATIENT);
        assertThat(scopes.get(1).context()).isEqualTo(SmartContext.USER);
    }

    // ── Ignored / non-FHIR scopes ─────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"openid", "profile", "email", "offline_access", "fhirUser",
                            "launch", "launch/patient", "launch/encounter"})
    void parse_nonFhirScope_returnsEmpty(String token) {
        assertThat(parser.parseToken(token)).isEmpty();
    }

    // ── Malformed tokens ──────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"foo", "foo/bar", "patient/", "/Patient.read",
                            "patient/Patient.", "patient/.read", ""})
    void parse_malformedToken_returnsEmpty(String token) {
        assertThat(parser.parseToken(token)).isEmpty();
    }

    @Test
    void parse_nullInput_returnsEmptyList() {
        assertThat(parser.parse(null)).isEmpty();
    }

    @Test
    void parse_blankInput_returnsEmptyList() {
        assertThat(parser.parse("   ")).isEmpty();
    }

    // ── Merge ────────────────────────────────────────────────────────────────

    @Test
    void merge_sameContextAndResource_unionsPermissions() {
        List<SmartScope> a = parser.parse("user/Patient.read");  // r,s
        List<SmartScope> b = parser.parse("user/Patient.write"); // c,u
        List<SmartScope> merged = parser.merge(a, b);

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).permissions())
                .containsExactlyInAnyOrder(
                        SmartPermission.READ, SmartPermission.SEARCH,
                        SmartPermission.CREATE, SmartPermission.UPDATE);
    }

    @Test
    void merge_differentResources_returnsBoth() {
        List<SmartScope> a = parser.parse("user/Patient.read");
        List<SmartScope> b = parser.parse("user/Observation.read");
        assertThat(parser.merge(a, b)).hasSize(2);
    }

    // ── toScopeString round-trip ──────────────────────────────────────────────

    @Test
    void toScopeString_roundTrip_isSymmetric() {
        SmartScope scope = parser.parseToken("user/Condition.crs").orElseThrow();
        SmartScope reparsed = parser.parseToken(scope.toScopeString()).orElseThrow();
        assertThat(reparsed.context()).isEqualTo(scope.context());
        assertThat(reparsed.resourceType()).isEqualTo(scope.resourceType());
        assertThat(reparsed.permissions()).isEqualTo(scope.permissions());
    }
}
