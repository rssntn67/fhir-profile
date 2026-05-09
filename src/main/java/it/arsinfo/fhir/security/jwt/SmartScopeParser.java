package it.arsinfo.fhir.security.jwt;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Parses SMART on FHIR scope strings (both v1 read/write and v2 cruds notation)
 * into {@link SmartScope} instances.
 *
 * Non-FHIR OAuth2 scopes (openid, profile, email, offline_access, fhirUser, launch,
 * launch/patient, launch/encounter) are silently ignored.
 */
@Component
public class SmartScopeParser {

    private static final Set<String> IGNORED_SCOPES = Set.of(
            "openid", "profile", "email", "address", "phone",
            "offline_access", "online_access",
            "fhirUser", "launch", "launch/patient", "launch/encounter"
    );

    /**
     * Parses a space-separated SMART scope string from the JWT "scope" claim.
     *
     * @param scopeString space-separated JWT scope claim value (may be null or blank)
     * @return ordered, de-duplicated list of parsed SmartScope instances
     */
    public List<SmartScope> parse(String scopeString) {
        if (scopeString == null || scopeString.isBlank()) {
            return List.of();
        }
        List<SmartScope> result = new ArrayList<>();
        for (String token : scopeString.trim().split("\\s+")) {
            parseToken(token).ifPresent(result::add);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Parses a single SMART scope token such as "patient/Patient.read".
     *
     * @return the parsed scope, or empty if the token is not a valid SMART FHIR scope
     */
    public Optional<SmartScope> parseToken(String token) {
        if (token == null || token.isBlank() || IGNORED_SCOPES.contains(token)) {
            return Optional.empty();
        }

        // Expected format: <context>/<resourceType>.<permissions>
        int slashIdx = token.indexOf('/');
        int dotIdx   = token.lastIndexOf('.');
        if (slashIdx < 0 || dotIdx < 0 || dotIdx < slashIdx) {
            return Optional.empty();
        }

        String contextPart   = token.substring(0, slashIdx);
        String resourcePart  = token.substring(slashIdx + 1, dotIdx);
        String permsPart     = token.substring(dotIdx + 1);

        SmartContext context;
        try {
            context = SmartContext.fromString(contextPart);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        if (resourcePart.isBlank() || permsPart.isBlank()) {
            return Optional.empty();
        }

        Set<SmartPermission> permissions;
        try {
            permissions = parsePermissions(permsPart);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        if (permissions.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new SmartScope(context, resourcePart, Collections.unmodifiableSet(permissions)));
    }

    /**
     * Merges two scope lists. Permissions for the same context+resourceType are unioned.
     */
    public List<SmartScope> merge(List<SmartScope> a, List<SmartScope> b) {
        // Key: context + "|" + resourceType → accumulated permissions
        Map<String, Set<SmartPermission>> merged = new LinkedHashMap<>();

        for (SmartScope scope : a) {
            String key = scope.context() + "|" + scope.resourceType();
            merged.computeIfAbsent(key, k -> EnumSet.noneOf(SmartPermission.class))
                  .addAll(scope.permissions());
        }
        for (SmartScope scope : b) {
            String key = scope.context() + "|" + scope.resourceType();
            merged.computeIfAbsent(key, k -> EnumSet.noneOf(SmartPermission.class))
                  .addAll(scope.permissions());
        }

        List<SmartScope> result = new ArrayList<>();
        merged.forEach((key, perms) -> {
            String[] parts   = key.split("\\|", 2);
            SmartContext ctx  = SmartContext.valueOf(parts[0]);
            String resource  = parts[1];
            result.add(new SmartScope(ctx, resource, Collections.unmodifiableSet(perms)));
        });
        return Collections.unmodifiableList(result);
    }

    // ── private ──────────────────────────────────────────────────────────────

    private Set<SmartPermission> parsePermissions(String perms) {
        return switch (perms.toLowerCase()) {
            case "read"      -> SmartPermission.fromLegacyRead();
            case "write"     -> SmartPermission.fromLegacyWrite();
            case "read+write",
                 "write+read",
                 "*"         -> SmartPermission.all();
            default          -> SmartPermission.fromV2String(perms);
        };
    }
}
