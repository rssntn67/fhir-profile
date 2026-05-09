package it.arsinfo.fhir.domain.enums;

import it.arsinfo.fhir.security.jwt.SmartContext;

import java.util.List;

/**
 * Canonical built-in roles shipped with the server.
 * These are seeded at startup (idempotent) and cannot be deleted via the UI.
 */
public enum BuiltInRole {

    SUPER_ADMIN(
        "Full unrestricted system access",
        SmartContext.SYSTEM,
        List.of(
            "system/*.cruds"
        )
    ),

    CLINICAL_ADMIN(
        "Clinical administration — read all, full write on patient/practitioner demographics",
        SmartContext.USER,
        List.of(
            "user/*.read",
            "user/Patient.cruds",
            "user/Practitioner.cruds",
            "user/Organization.cruds",
            "user/Location.cruds"
        )
    ),

    PRACTITIONER(
        "Healthcare provider — full clinical data CRUD, read-only on patient demographics",
        SmartContext.USER,
        List.of(
            "user/Patient.read",
            "user/Observation.cruds",
            "user/Condition.cruds",
            "user/Procedure.cruds",
            "user/MedicationRequest.cruds",
            "user/MedicationStatement.cruds",
            "user/DiagnosticReport.cruds",
            "user/Encounter.cruds",
            "user/AllergyIntolerance.cruds",
            "user/Immunization.cruds",
            "user/CarePlan.cruds"
        )
    ),

    NURSE(
        "Nursing staff — observations and vitals, read clinical data, no prescribing",
        SmartContext.USER,
        List.of(
            "user/Patient.read",
            "user/Observation.cruds",
            "user/Condition.read",
            "user/MedicationRequest.read",
            "user/MedicationStatement.read",
            "user/AllergyIntolerance.read",
            "user/Immunization.cruds",
            "user/Encounter.read"
        )
    ),

    PATIENT(
        "Patient self-access — read own compartment only",
        SmartContext.PATIENT,
        List.of(
            "patient/Patient.read",
            "patient/Observation.read",
            "patient/Condition.read",
            "patient/MedicationRequest.read",
            "patient/MedicationStatement.read",
            "patient/DiagnosticReport.read",
            "patient/AllergyIntolerance.read",
            "patient/Immunization.read",
            "patient/Encounter.read",
            "patient/CarePlan.read"
        )
    ),

    READONLY(
        "Read-only access across all resources — audit, BI, reporting",
        SmartContext.USER,
        List.of(
            "user/*.read"
        )
    );

    public final String description;
    public final SmartContext smartContext;
    public final List<String> scopeStrings;

    BuiltInRole(String description, SmartContext smartContext, List<String> scopeStrings) {
        this.description  = description;
        this.smartContext = smartContext;
        this.scopeStrings = scopeStrings;
    }
}
