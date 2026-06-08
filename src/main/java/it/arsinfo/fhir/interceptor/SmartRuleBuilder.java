package it.arsinfo.fhir.interceptor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;
import it.arsinfo.fhir.security.jwt.SmartContext;
import it.arsinfo.fhir.security.jwt.SmartPermission;
import it.arsinfo.fhir.security.jwt.SmartScope;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.IdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Translates a list of {@link SmartScope} objects into HAPI FHIR {@link IAuthRule} instances.
 *
 * Mapping table:
 *
 *  system/*.cruds / user/*.cruds → allow all operations on all resources (no compartment)
 *  system/<T>.r   / user/<T>.r   → allow read  on T, any ID
 *  system/<T>.c   / user/<T>.c   → allow create on T
 *  system/<T>.u   / user/<T>.u   → allow write (update) on T
 *  system/<T>.d   / user/<T>.d   → allow delete on T
 *  patient/<T>.r               → allow read  on T in Patient/<id> compartment
 *  patient/<T>.c               → allow create on T in Patient/<id> compartment
 *  patient/<T>.u               → allow write  on T in Patient/<id> compartment
 *  patient/<T>.d               → allow delete on T in Patient/<id> compartment
 *
 *  PATIENT context with no patient ID → zero allow rules (request is effectively denied).
 *
 * The final rule list always ends with a catch-all denyAll().
 * A standalone allow().metadata() rule is prepended so the capability statement is always public.
 */
@Component
public class SmartRuleBuilder {

    private static final Logger log = LoggerFactory.getLogger(SmartRuleBuilder.class);

    private final FhirContext fhirContext;

    public SmartRuleBuilder(FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    /**
     * Builds the complete ordered IAuthRule list for the current request.
     *
     * @param effectiveScopes merged SMART scopes (JWT + role-based)
     * @param patientId       patient context from JWT "patient" claim (required for PATIENT scopes)
     * @return HAPI rule list, always terminated with denyAll()
     */
    public List<IAuthRule> buildRules(List<SmartScope> effectiveScopes, Optional<String> patientId) {
        List<IAuthRule> rules = new ArrayList<>();
        AtomicInteger ruleIndex = new AtomicInteger(0);

        // Always permit the capability statement (GET /fhir/metadata)
        rules.addAll(new RuleBuilder()
                .allow("r-" + ruleIndex.getAndIncrement() + "-metadata")
                .metadata()
                .build());

        for (SmartScope scope : effectiveScopes) {
            List<IAuthRule> scopeRules = switch (scope.context()) {
                case SYSTEM, USER -> buildUnboundedRules(scope, ruleIndex);
                case PATIENT      -> buildPatientCompartmentRules(scope, patientId, ruleIndex);
            };
            if (scopeRules.isEmpty() && scope.context() == SmartContext.PATIENT && patientId.isEmpty()) {
                log.warn("PATIENT scope '{}' present but JWT has no patient context — access denied for this scope", scope);
            }
            rules.addAll(scopeRules);
        }

        rules.addAll(addValidateRules(ruleIndex));
        rules.addAll(addEverythingRules(effectiveScopes, patientId, ruleIndex));
        rules.addAll(addSystemHistoryRules(effectiveScopes, ruleIndex));

        // Catch-all deny — must be last
        rules.addAll(new RuleBuilder().denyAll("deny-all-unmatched").build());
        return rules;
    }

    private List<IAuthRule> addSystemHistoryRules(List<SmartScope> scopes, AtomicInteger idx) {
        boolean hasSystemReadAll = scopes.stream()
                .anyMatch(s -> s.context() == SmartContext.SYSTEM
                        && s.isWildcard()
                        && (s.allows(SmartPermission.READ) || s.allows(SmartPermission.SEARCH)));
        if (!hasSystemReadAll) return List.of();

        return new RuleBuilder()
                .allow("r-" + idx.getAndIncrement() + "-system-history")
                .operation()
                .named("_history")
                .atAnyLevel()
                .andAllowAllResponses()
                .build();
    }

    private List<IAuthRule> addEverythingRules(List<SmartScope> scopes,
                                               Optional<String> patientId,
                                               AtomicInteger idx) {
        List<IAuthRule> rules = new ArrayList<>();
        for (SmartScope scope : scopes) {
            if (!scope.allows(SmartPermission.READ) && !scope.allows(SmartPermission.SEARCH)) {
                continue;
            }
            boolean appliesToPatient = scope.isWildcard() || "Patient".equals(scope.resourceType());
            if (!appliesToPatient) continue;

            String prefix = "r-" + idx.getAndIncrement() + "-everything";
            if (scope.context() == SmartContext.PATIENT && patientId.isPresent()) {
                IIdType ref = new IdType("Patient", extractPatientIdValue(patientId.get()));
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "-patient")
                        .operation().named("$everything")
                        .onInstance(ref)
                        .andAllowAllResponses()
                        .build());
            } else if (scope.context() == SmartContext.USER || scope.context() == SmartContext.SYSTEM) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "-user")
                        .operation().named("$everything")
                        .onAnyInstance()
                        .andAllowAllResponses()
                        .build());
            }
        }
        return rules;
    }

    private List<IAuthRule> addValidateRules(AtomicInteger idx) {
        return new RuleBuilder()
                .allow("r-" + idx.getAndIncrement() + "-validate")
                .operation()
                .named("$validate")
                .atAnyLevel()
                .andAllowAllResponses()
                .build();
    }

    // ── private: unbounded (SYSTEM / USER) ───────────────────────────────────

    private List<IAuthRule> buildUnboundedRules(SmartScope scope, AtomicInteger idx) {
        List<IAuthRule> rules = new ArrayList<>();
        String prefix = "r-" + idx.getAndIncrement() + "-" + scope.context().name().toLowerCase()
                        + "-" + scope.resourceType() + "-";

        if (scope.isWildcard()) {
            // Wildcard: operate on all resource types
            if (scope.allows(SmartPermission.READ) || scope.allows(SmartPermission.SEARCH)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "read-all").read().allResources().withAnyId().build());
            }
            if (scope.allows(SmartPermission.CREATE)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "create-all").create().allResources().withAnyId().build());
            }
            if (scope.allows(SmartPermission.UPDATE)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "write-all").write().allResources().withAnyId().build());
            }
            if (scope.allows(SmartPermission.DELETE)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "delete-all").delete().allResources().withAnyId().build());
            }
        } else {
            // Specific resource type
            if (scope.allows(SmartPermission.READ) || scope.allows(SmartPermission.SEARCH)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "read").read()
                        .resourcesOfType(scope.resourceType()).withAnyId().build());
            }
            if (scope.allows(SmartPermission.CREATE)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "create").create()
                        .resourcesOfType(scope.resourceType()).withAnyId().build());
            }
            if (scope.allows(SmartPermission.UPDATE)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "write").write()
                        .resourcesOfType(scope.resourceType()).withAnyId().build());
            }
            if (scope.allows(SmartPermission.DELETE)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "delete").delete()
                        .resourcesOfType(scope.resourceType()).withAnyId().build());
            }
        }
        return rules;
    }

    // ── private: compartment-bounded (PATIENT) ────────────────────────────────

    private List<IAuthRule> buildPatientCompartmentRules(SmartScope scope,
                                                         Optional<String> patientId,
                                                         AtomicInteger idx) {
        if (patientId.isEmpty()) {
            return List.of(); // no compartment → no allow rule → effectively denied
        }

        IIdType patientRef = new IdType("Patient", extractPatientIdValue(patientId.get()));
        String prefix = "r-" + idx.getAndIncrement() + "-patient-" + scope.resourceType() + "-";
        List<IAuthRule> rules = new ArrayList<>();

        if (scope.isWildcard()) {
            if (scope.allows(SmartPermission.READ) || scope.allows(SmartPermission.SEARCH)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "read-all")
                        .read().allResources().inCompartment("Patient", patientRef).build());
            }
            if (scope.allows(SmartPermission.CREATE)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "create-all")
                        .create().allResources().inCompartment("Patient", patientRef).build());
            }
            if (scope.allows(SmartPermission.UPDATE)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "write-all")
                        .write().allResources().inCompartment("Patient", patientRef).build());
            }
            if (scope.allows(SmartPermission.DELETE)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "delete-all")
                        .delete().allResources().inCompartment("Patient", patientRef).build());
            }
        } else {
            if (scope.allows(SmartPermission.READ) || scope.allows(SmartPermission.SEARCH)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "read")
                        .read().resourcesOfType(scope.resourceType())
                        .inCompartment("Patient", patientRef).build());
            }
            if (scope.allows(SmartPermission.CREATE)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "create")
                        .create().resourcesOfType(scope.resourceType())
                        .inCompartment("Patient", patientRef).build());
            }
            if (scope.allows(SmartPermission.UPDATE)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "write")
                        .write().resourcesOfType(scope.resourceType())
                        .inCompartment("Patient", patientRef).build());
            }
            if (scope.allows(SmartPermission.DELETE)) {
                rules.addAll(new RuleBuilder()
                        .allow(prefix + "delete")
                        .delete().resourcesOfType(scope.resourceType())
                        .inCompartment("Patient", patientRef).build());
            }
        }
        return rules;
    }

    // Strip "Patient/" prefix if present so we get just the logical ID
    private String extractPatientIdValue(String raw) {
        return raw.startsWith("Patient/") ? raw.substring("Patient/".length()) : raw;
    }
}
