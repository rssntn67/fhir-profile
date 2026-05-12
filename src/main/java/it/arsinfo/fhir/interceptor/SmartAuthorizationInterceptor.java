package it.arsinfo.fhir.interceptor;

import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;
import it.arsinfo.fhir.security.jwt.JwtClaimsExtractor;
import it.arsinfo.fhir.security.jwt.SmartScope;
import it.arsinfo.fhir.security.rbac.RbacScopeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * HAPI FHIR authorization interceptor that enforces SMART-on-FHIR RBAC.
 *
 * Called by HAPI for every FHIR operation before execution. It reads the
 * JWT from the Spring Security context (populated by the OAuth2 resource server
 * filter chain before the request reaches the FHIR servlet), resolves effective
 * SMART scopes, and delegates rule construction to {@link SmartRuleBuilder}.
 *
 * If no authenticated principal is present the request is denied entirely.
 */
@Component
public class SmartAuthorizationInterceptor extends AuthorizationInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SmartAuthorizationInterceptor.class);

    private final RbacScopeResolver scopeResolver;
    private final SmartRuleBuilder  ruleBuilder;
    private final JwtClaimsExtractor claimsExtractor;

    public SmartAuthorizationInterceptor(RbacScopeResolver scopeResolver,
                                         SmartRuleBuilder ruleBuilder,
                                         JwtClaimsExtractor claimsExtractor) {
        this.scopeResolver    = scopeResolver;
        this.ruleBuilder      = ruleBuilder;
        this.claimsExtractor  = claimsExtractor;
    }

    @Override
    public List<IAuthRule> buildRuleList(RequestDetails theRequestDetails) {
        Optional<Jwt> jwtOpt = extractJwt();

        if (jwtOpt.isEmpty()) {
            log.debug("No JWT found in SecurityContext — allowing only metadata, denying rest");
            return new RuleBuilder()
                    .allow("r-anon-metadata").metadata()
                    .andThen()
                    .denyAll("No authenticated principal")
                    .build();
        }

        Jwt jwt = jwtOpt.get();
        String subject = claimsExtractor.extractSubject(jwt);

        // Resolve all effective SMART scopes (JWT + role-based + DB)
        List<SmartScope> effectiveScopes = scopeResolver.resolveEffectiveScopes(jwt);

        // Extract patient context for compartment-bounded scopes
        Optional<String> patientId = claimsExtractor.extractPatientId(jwt);

        log.debug("Authorization — subject={}, scopes={}, patientId={}", subject, effectiveScopes, patientId);

        return ruleBuilder.buildRules(effectiveScopes, patientId);
    }

    /**
     * Extracts the Spring Security {@link Jwt} from the current security context.
     * Returns empty when the request is unauthenticated or uses a different token type.
     */
    private Optional<Jwt> extractJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtToken) {
            return Optional.of(jwtToken.getToken());
        }
        return Optional.empty();
    }
}
