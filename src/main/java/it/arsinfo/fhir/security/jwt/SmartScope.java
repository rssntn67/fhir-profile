package it.arsinfo.fhir.security.jwt;

import java.util.Set;

/**
 * Immutable value object representing one parsed SMART on FHIR scope.
 *
 * Examples:
 *   "patient/Patient.read"   → context=PATIENT, resourceType="Patient", permissions={READ,SEARCH}
 *   "user/*.cruds"           → context=USER,    resourceType="*",       permissions={all}
 *   "system/Observation.ru"  → context=SYSTEM,  resourceType="Observation", permissions={READ,UPDATE}
 */
public record SmartScope(
        SmartContext context,
        String resourceType,
        Set<SmartPermission> permissions
) {

    /** True when this scope applies to all resource types. */
    public boolean isWildcard() {
        return "*".equals(resourceType);
    }

    public boolean allows(SmartPermission permission) {
        return permissions.contains(permission);
    }

    /** Reconstructs the canonical scope string (e.g. "user/Patient.crs"). */
    public String toScopeString() {
        StringBuilder perms = new StringBuilder();
        if (permissions.contains(SmartPermission.CREATE)) perms.append('c');
        if (permissions.contains(SmartPermission.READ))   perms.append('r');
        if (permissions.contains(SmartPermission.UPDATE))  perms.append('u');
        if (permissions.contains(SmartPermission.DELETE))  perms.append('d');
        if (permissions.contains(SmartPermission.SEARCH))  perms.append('s');
        return context.name().toLowerCase() + "/" + resourceType + "." + perms;
    }

    @Override
    public String toString() {
        return toScopeString();
    }
}
