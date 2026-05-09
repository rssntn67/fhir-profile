package it.arsinfo.fhir.security.rbac;

import it.arsinfo.fhir.config.RbacProperties;
import it.arsinfo.fhir.domain.entity.UserRoleAssignment;
import it.arsinfo.fhir.repository.UserRoleAssignmentRepository;
import it.arsinfo.fhir.security.jwt.JwtClaimsExtractor;
import it.arsinfo.fhir.security.jwt.SmartScope;
import it.arsinfo.fhir.security.jwt.SmartScopeParser;
import it.arsinfo.fhir.repository.RoleRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the complete, effective list of SMART scopes for an authenticated request
 * by merging three sources:
 *
 *  1. SMART scopes embedded directly in the JWT "scope" claim.
 *  2. Scopes inherited from roles listed in the JWT claims (e.g. realm_access.roles).
 *  3. Scopes inherited from local DB role assignments for the JWT subject (when enabled).
 */
@Service
@Transactional(readOnly = true)
public class RbacScopeResolver {

    private final JwtClaimsExtractor extractor;
    private final SmartScopeParser parser;
    private final RbacProperties props;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository assignmentRepository;

    public RbacScopeResolver(JwtClaimsExtractor extractor,
                             SmartScopeParser parser,
                             RbacProperties props,
                             RoleRepository roleRepository,
                             UserRoleAssignmentRepository assignmentRepository) {
        this.extractor            = extractor;
        this.parser               = parser;
        this.props                = props;
        this.roleRepository       = roleRepository;
        this.assignmentRepository = assignmentRepository;
    }

    /**
     * Resolves and merges all effective SMART scopes for the given JWT.
     *
     * @param jwt Spring Security Jwt for the current request
     * @return merged, de-duplicated list of SmartScope instances
     */
    public List<SmartScope> resolveEffectiveScopes(Jwt jwt) {
        // 1. JWT scope claim
        List<SmartScope> jwtScopes = parser.parse(extractor.extractScopeString(jwt));

        // 2. Scopes from roles declared in the JWT claim (e.g. Keycloak realm_access.roles)
        List<String> jwtRoleNames = extractor.extractRoleNames(jwt);
        List<SmartScope> jwtRoleScopes = loadScopesForRoleNames(jwtRoleNames);

        // 3. DB-assigned role scopes (optional)
        List<SmartScope> dbScopes = List.of();
        if (props.isDbRolesEnabled()) {
            String subject = extractor.extractSubject(jwt);
            List<UserRoleAssignment> assignments = assignmentRepository.findActiveBySubject(subject);
            List<String> dbRoleNames = assignments.stream()
                    .map(a -> a.getRole().getName())
                    .toList();
            dbScopes = loadScopesForRoleNames(dbRoleNames);
        }

        // Merge all three sources
        List<SmartScope> merged = parser.merge(jwtScopes, jwtRoleScopes);
        merged = parser.merge(merged, dbScopes);
        return merged;
    }

    // ── private ──────────────────────────────────────────────────────────────

    private List<SmartScope> loadScopesForRoleNames(List<String> roleNames) {
        if (roleNames.isEmpty()) return List.of();
        List<SmartScope> result = new ArrayList<>();
        roleRepository.findByNameInWithScopes(roleNames).forEach(role ->
            role.getScopes().forEach(rs ->
                parser.parseToken(rs.getScopeString()).ifPresent(result::add)
            )
        );
        return result;
    }
}
