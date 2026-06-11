package it.arsinfo.fhir;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class FhirJpaIT {

    @LocalServerPort int port;

    @Autowired TestRestTemplate rest;

    @MockBean JwtDecoder jwtDecoder;
    @MockBean ClientRegistrationRepository clientRegistrationRepository;

    private static final String FHIR_JSON = "application/fhir+json";
    private static final String TOKEN_ALL  = "token-all";   // scope: user/*.cruds
    private static final String TOKEN_READ = "token-read";  // scope: user/*.read
    private static final String TOKEN_SYS  = "token-sys";   // scope: system/*.read

    @BeforeEach
    void setupJwt() {
        when(jwtDecoder.decode(TOKEN_ALL)).thenReturn(jwt("user/*.cruds"));
        when(jwtDecoder.decode(TOKEN_READ)).thenReturn(jwt("user/*.read"));
        when(jwtDecoder.decode(TOKEN_SYS)).thenReturn(jwt("system/*.read"));
    }

    private Jwt jwt(String scope) {
        return Jwt.withTokenValue("tok")
                .header("alg", "none")
                .claim("scope", scope)
                .claim("sub", "test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private String base() { return "http://localhost:" + port; }

    private HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + token);
        h.setContentType(MediaType.parseMediaType(FHIR_JSON));
        h.set("Accept", FHIR_JSON);
        return h;
    }

    // ── Capability statement ─────────────────────────────────────────────────

    @Test
    void metadata_isPublic_noAuth() {
        ResponseEntity<String> resp = rest.getForEntity(base() + "/fhir/metadata", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"fhirVersion\"");
    }

    // ── Patient CRUD ─────────────────────────────────────────────────────────

    @Test
    void createAndReadPatient_roundTrip() {
        String body = """
            {"resourceType":"Patient","name":[{"family":"Testmann","given":["Hans"]}],"gender":"male"}
            """;
        ResponseEntity<String> created = rest.exchange(
                base() + "/fhir/Patient", HttpMethod.POST,
                new HttpEntity<>(body, bearer(TOKEN_ALL)), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String location = created.getHeaders().getFirst("Location");
        assertThat(location).isNotNull();
        String id = location.replaceAll(".*/Patient/(\\w+)/.*", "$1");

        ResponseEntity<String> read = rest.exchange(
                base() + "/fhir/Patient/" + id, HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_READ)), String.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody()).contains("Testmann");
    }

    @Test
    void readPatient_noAuth_returns401or403() {
        ResponseEntity<String> resp = rest.getForEntity(base() + "/fhir/Patient/1", String.class);
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    // ── Search ───────────────────────────────────────────────────────────────

    @Test
    void searchPatient_byFamily_returnsBundle() {
        String body = """
            {"resourceType":"Patient","name":[{"family":"Searchable99","given":["Test"]}]}
            """;
        rest.exchange(base() + "/fhir/Patient", HttpMethod.POST,
                new HttpEntity<>(body, bearer(TOKEN_ALL)), String.class);

        ResponseEntity<String> search = rest.exchange(
                base() + "/fhir/Patient?family=Searchable99", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_READ)), String.class);
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(search.getBody()).contains("\"Bundle\"");
    }

    // ── $validate ────────────────────────────────────────────────────────────

    @Test
    void validateOperation_allowedWithAnyAuthenticatedScope() {
        String body = """
            {"resourceType":"Patient","name":[{"family":"ValidateMe"}]}
            """;
        ResponseEntity<String> resp = rest.exchange(
                base() + "/fhir/Patient/$validate", HttpMethod.POST,
                new HttpEntity<>(body, bearer(TOKEN_READ)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("OperationOutcome");
    }

    // ── $everything ──────────────────────────────────────────────────────────

    @Test
    void everythingOperation_allowedWithUserPatientRead() {
        // Create a patient
        String body = """
            {"resourceType":"Patient","name":[{"family":"EverythingTest99"}]}
            """;
        ResponseEntity<String> created = rest.exchange(
                base() + "/fhir/Patient", HttpMethod.POST,
                new HttpEntity<>(body, bearer(TOKEN_ALL)), String.class);
        String id = created.getHeaders().getFirst("Location")
                .replaceAll(".*/Patient/(\\w+)/.*", "$1");

        ResponseEntity<String> resp = rest.exchange(
                base() + "/fhir/Patient/" + id + "/$everything", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_READ)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"Bundle\"");
    }

    @Test
    void everythingOperation_deniedWithObservationOnlyScope() {
        when(jwtDecoder.decode("token-obs")).thenReturn(jwt("user/Observation.read"));
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer token-obs");
        h.set("Accept", FHIR_JSON);

        ResponseEntity<String> resp = rest.exchange(
                base() + "/fhir/Patient/1/$everything", HttpMethod.GET,
                new HttpEntity<>(h), String.class);
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    // ── _history ─────────────────────────────────────────────────────────────

    @Test
    void resourceHistory_allowedWithReadScope() {
        String body = """
            {"resourceType":"Patient","name":[{"family":"HistoryTest99"}]}
            """;
        ResponseEntity<String> created = rest.exchange(
                base() + "/fhir/Patient", HttpMethod.POST,
                new HttpEntity<>(body, bearer(TOKEN_ALL)), String.class);
        String id = created.getHeaders().getFirst("Location")
                .replaceAll(".*/Patient/(\\w+)/.*", "$1");

        ResponseEntity<String> resp = rest.exchange(
                base() + "/fhir/Patient/" + id + "/_history", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_READ)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"Bundle\"");
    }

    @Test
    void systemHistory_allowedWithSystemScope() {
        ResponseEntity<String> resp = rest.exchange(
                base() + "/fhir/_history", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_SYS)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"Bundle\"");
    }

    @Test
    void systemHistory_deniedWithUserScope() {
        ResponseEntity<String> resp = rest.exchange(
                base() + "/fhir/_history", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_READ)), String.class);
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }
}
