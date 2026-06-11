package it.arsinfo.fhir.interceptor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import it.arsinfo.fhir.security.jwt.SmartScopeParser;
import it.arsinfo.fhir.security.jwt.SmartScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class SmartRuleBuilderTest {

    private SmartRuleBuilder ruleBuilder;
    private SmartScopeParser parser;

    @BeforeEach
    void setUp() {
        FhirContext ctx = FhirContext.forR4();
        ruleBuilder = new SmartRuleBuilder(ctx);
        parser      = new SmartScopeParser();
    }

    @Test
    void buildRules_noScopes_resultContainsMetadataAndDenyAll() {
        List<IAuthRule> rules = ruleBuilder.buildRules(List.of(), Optional.empty());
        // At minimum: metadata allow + deny-all
        assertThat(rules).hasSizeGreaterThanOrEqualTo(2);
        String lastRuleName = rules.get(rules.size() - 1).getName();
        assertThat(lastRuleName).contains("deny");
    }

    @Test
    void buildRules_systemWildcardCruds_producesMultipleAllowRules() {
        List<SmartScope> scopes = parser.parse("system/*.cruds");
        List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.empty());

        // metadata + (read + create + write + delete for all) + deny = >= 6 rules
        assertThat(rules).hasSizeGreaterThanOrEqualTo(6);
    }

    @Test
    void buildRules_patientScopeWithPatientId_generatesCompartmentRules() {
        List<SmartScope> scopes = parser.parse("patient/Patient.read");
        List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.of("Patient/abc123"));

        // metadata + at least one compartment-bounded allow + deny
        assertThat(rules).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void buildRules_patientScopeWithoutPatientId_producesNoExtraAllowRules() {
        List<SmartScope> scopes = parser.parse("patient/Patient.read");
        List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.empty());

        // metadata + denyAll, no compartment allow rules because patientId is absent
        // ($validate is always added as a general operation rule)
        assertThat(rules.get(0).getName()).contains("metadata");
        assertThat(rules.get(rules.size() - 1).getName()).contains("deny");
        boolean hasCompartmentAllow = rules.stream()
                .anyMatch(r -> r.getName() != null && r.getName().contains("patient-Patient"));
        assertThat(hasCompartmentAllow).isFalse();
    }

    @Test
    void buildRules_alwaysTerminatesWithDenyAll() {
        List<SmartScope> scopes = parser.parse("user/Patient.read user/Observation.cruds");
        List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.empty());
        assertThat(rules.get(rules.size() - 1).getName()).contains("deny");
    }

    @Test
    void buildRules_firstRuleIsAlwaysMetadata() {
        List<IAuthRule> rules = ruleBuilder.buildRules(List.of(), Optional.empty());
        assertThat(rules.get(0).getName()).contains("metadata");
    }

    @Test
    void buildRules_systemHistoryAllowed_forSystemReadScope() {
        List<SmartScope> scopes = parser.parse("system/*.read");
        List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.empty());

        boolean hasHistoryRule = rules.stream()
                .anyMatch(r -> r.getName() != null && r.getName().contains("history"));
        assertThat(hasHistoryRule).isTrue();
    }

    @Test
    void buildRules_systemHistoryNotAllowed_forUserReadScope() {
        // user/*.read must NOT grant an ALLOW rule for system-level _history.
        // An explicit deny rule is expected to prevent the general "read all resources" rule
        // from leaking through to the server-level _history endpoint.
        List<SmartScope> scopes = parser.parse("user/*.read");
        List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.empty());

        boolean hasSystemHistoryAllow = rules.stream()
                .anyMatch(r -> r.getName() != null
                        && r.getName().contains("system-history")
                        && !r.getName().contains("deny"));
        assertThat(hasSystemHistoryAllow).isFalse();

        // And there must be an explicit deny to protect the endpoint
        boolean hasDenyRule = rules.stream()
                .anyMatch(r -> r.getName() != null && r.getName().contains("deny-server-history"));
        assertThat(hasDenyRule).isTrue();
    }

    @Test
    void buildRules_everythingAllowed_whenUserHasPatientRead() {
        List<SmartScope> scopes = parser.parse("user/Patient.read");
        List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.empty());

        boolean hasEverythingRule = rules.stream()
                .anyMatch(r -> r.getName() != null && r.getName().contains("everything"));
        assertThat(hasEverythingRule).isTrue();
    }

    @Test
    void buildRules_everythingAllowed_forPatientContextWithMatchingId() {
        List<SmartScope> scopes = parser.parse("patient/Patient.read");
        List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.of("Patient/p1"));

        boolean hasEverythingRule = rules.stream()
                .anyMatch(r -> r.getName() != null && r.getName().contains("everything"));
        assertThat(hasEverythingRule).isTrue();
    }

    @Test
    void buildRules_validateOperationAllowed_regardlessOfScopes() {
        // $validate does not read or write data — always allow for any authenticated user
        List<SmartScope> scopes = parser.parse("user/Patient.read");
        List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.empty());

        boolean hasValidateRule = rules.stream()
                .anyMatch(r -> r.getName() != null && r.getName().contains("validate"));
        assertThat(hasValidateRule).isTrue();
    }

    @Test
    void buildRules_userReadOnly_producesReadRulesButNoWriteRules() {
        List<SmartScope> scopes = parser.parse("user/Patient.read");
        List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.empty());

        boolean hasReadAllow = rules.stream()
                .anyMatch(r -> r.getName().contains("read") && !r.getName().contains("deny"));
        boolean hasWriteAllow = rules.stream()
                .anyMatch(r -> r.getName().contains("write") && !r.getName().contains("deny"));
        boolean hasDeleteAllow = rules.stream()
                .anyMatch(r -> r.getName().contains("delete") && !r.getName().contains("deny"));

        assertThat(hasReadAllow).isTrue();
        assertThat(hasWriteAllow).isFalse();
        assertThat(hasDeleteAllow).isFalse();
    }
}
