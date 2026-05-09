package it.arsinfo.fhir.security.jwt;

/**
 * SMART on FHIR launch context — determines the audience and compartment of a scope.
 *
 * PATIENT — access limited to the compartment of the patient identified in the JWT.
 * USER    — access limited to what the authenticated user is allowed to see.
 * SYSTEM  — backend service access; no user or patient context required.
 */
public enum SmartContext {
    PATIENT,
    USER,
    SYSTEM;

    public static SmartContext fromString(String value) {
        return switch (value.toLowerCase()) {
            case "patient" -> PATIENT;
            case "user"    -> USER;
            case "system"  -> SYSTEM;
            default        -> throw new IllegalArgumentException("Unknown SMART context: " + value);
        };
    }
}
