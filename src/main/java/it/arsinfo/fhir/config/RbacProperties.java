package it.arsinfo.fhir.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

/**
 * All RBAC-related configuration, externalised to application.yml.
 * Change only YAML to switch AAA providers.
 */
@ConfigurationProperties(prefix = "fhir.rbac")
@Validated
public class RbacProperties {

    /**
     * Dot-notation path into the JWT claims map for the user's roles list.
     * Examples:
     *   Keycloak realm:  realm_access.roles
     *   Keycloak client: resource_access.fhir-server.roles
     *   Auth0 / Azure:   roles
     *   Okta:            groups
     *   Cognito:         cognito:groups
     */
    @NotBlank
    private String rolesClaim = "realm_access.roles";

    /** JWT claim that holds the SMART scope string (space-separated). */
    @NotBlank
    private String scopeClaim = "scope";

    /**
     * JWT claim that holds the patient context ID for SMART patient launch.
     * Standard SMART IG uses "patient"; some IdPs nest it in launch_context.patient.
     */
    @NotBlank
    private String patientClaim = "patient";

    /**
     * Secondary fallback path for patient ID when the primary claim is absent.
     * E.g. "launch_context.patient" for older Keycloak SMART plugins.
     */
    private String patientClaimFallback = "launch_context.patient";

    /** When true, also loads role assignments from the local database. */
    private boolean dbRolesEnabled = true;

    /** Scopes present in JWT that carry no FHIR meaning and should be skipped. */
    private Set<String> ignoredScopes = Set.of(
            "openid", "profile", "email", "offline_access", "fhirUser",
            "launch", "launch/patient", "launch/encounter"
    );

    // ── getters / setters ──────────────────────────────────────────────────

    public String getRolesClaim()              { return rolesClaim; }
    public void   setRolesClaim(String v)      { this.rolesClaim = v; }

    public String getScopeClaim()              { return scopeClaim; }
    public void   setScopeClaim(String v)      { this.scopeClaim = v; }

    public String getPatientClaim()            { return patientClaim; }
    public void   setPatientClaim(String v)    { this.patientClaim = v; }

    public String getPatientClaimFallback()    { return patientClaimFallback; }
    public void   setPatientClaimFallback(String v) { this.patientClaimFallback = v; }

    public boolean isDbRolesEnabled()          { return dbRolesEnabled; }
    public void    setDbRolesEnabled(boolean v){ this.dbRolesEnabled = v; }

    public Set<String> getIgnoredScopes()      { return ignoredScopes; }
    public void        setIgnoredScopes(Set<String> v) { this.ignoredScopes = v; }
}
