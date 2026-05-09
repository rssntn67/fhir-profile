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

        // Only metadata + denyAll — no compartment allows because patientId is absent
        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).getName()).contains("metadata");
        assertThat(rules.get(1).getName()).contains("deny");
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
