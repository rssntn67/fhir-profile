package it.arsinfo.fhir.security.jwt;

import it.arsinfo.fhir.config.RbacProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Extracts standard and custom claims from a Spring Security {@link Jwt}.
 *
 * All claim paths support dot-notation so the same code works for
 * Keycloak (realm_access.roles), Auth0 (roles), Okta (groups), etc.
 */
@Component
public class JwtClaimsExtractor {

    private final RbacProperties props;

    public JwtClaimsExtractor(RbacProperties props) {
        this.props = props;
    }

    /** Returns the JWT "sub" claim. */
    public String extractSubject(Jwt jwt) {
        return jwt.getSubject();
    }

    /**
     * Extracts the patient context ID. Checks the primary claim first,
     * then falls back to {@code patientClaimFallback} (e.g. launch_context.patient).
     */
    public Optional<String> extractPatientId(Jwt jwt) {
        Object primary = resolvePath(jwt.getClaims(), props.getPatientClaim());
        if (primary instanceof String s && !s.isBlank()) {
            return Optional.of(normalisePatientId(s));
        }
        Object fallback = resolvePath(jwt.getClaims(), props.getPatientClaimFallback());
        if (fallback instanceof String s && !s.isBlank()) {
            return Optional.of(normalisePatientId(s));
        }
        return Optional.empty();
    }

    /**
     * Extracts role names from the configured dot-notation claim path.
     * Returns an empty list if the path is absent or does not resolve to a List.
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoleNames(Jwt jwt) {
        Object value = resolvePath(jwt.getClaims(), props.getRolesClaim());
        if (value instanceof List<?> list) {
            return list.stream()
                       .filter(String.class::isInstance)
                       .map(String.class::cast)
                       .toList();
        }
        return List.of();
    }

    /**
     * Returns the raw SMART scope string from the JWT "scope" claim.
     * Returns an empty string if the claim is absent.
     */
    public String extractScopeString(Jwt jwt) {
        Object value = jwt.getClaim(props.getScopeClaim());
        return value instanceof String s ? s : "";
    }

    /**
     * Walks a dot-notation path through the JWT claims map.
     * E.g. "realm_access.roles" → jwt["realm_access"]["roles"].
     *
     * Package-visible for unit testing.
     */
    @SuppressWarnings("unchecked")
    Object resolvePath(Map<String, Object> claims, String dotPath) {
        if (dotPath == null || dotPath.isBlank()) return null;
        String[] parts = dotPath.split("\\.", -1);
        Object current = claims;
        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(part);
        }
        return current;
    }

    // ── private ──────────────────────────────────────────────────────────────

    /** Ensures patient ID is in "Patient/xxx" or plain "xxx" form. */
    private String normalisePatientId(String raw) {
        return raw.startsWith("Patient/") ? raw : raw;
    }
}
