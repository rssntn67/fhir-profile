package it.arsinfo.fhir.service;

import it.arsinfo.fhir.domain.entity.Role;
import it.arsinfo.fhir.domain.entity.RoleScope;
import it.arsinfo.fhir.repository.RoleRepository;
import it.arsinfo.fhir.repository.RoleScopeRepository;
import it.arsinfo.fhir.security.jwt.SmartScopeParser;
import it.arsinfo.fhir.web.dto.RoleScopeDto;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RoleScopeService {

    private final RoleScopeRepository scopeRepository;
    private final RoleRepository roleRepository;
    private final SmartScopeParser parser;

    public RoleScopeService(RoleScopeRepository scopeRepository,
                            RoleRepository roleRepository,
                            SmartScopeParser parser) {
        this.scopeRepository = scopeRepository;
        this.roleRepository  = roleRepository;
        this.parser          = parser;
    }

    @Transactional(readOnly = true)
    public List<RoleScope> findByRole(Long roleId) {
        return scopeRepository.findByRoleId(roleId);
    }

    public RoleScope addScope(Long roleId, RoleScopeDto dto) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleId));

        String scopeString = dto.getScopeString().trim();

        // Validate it is a parsable SMART scope
        parser.parseToken(scopeString)
              .orElseThrow(() -> new IllegalArgumentException(
                      "Invalid SMART scope string: '" + scopeString + "'. " +
                      "Expected format: <context>/<ResourceType>.<permissions>  " +
                      "(e.g. user/Patient.read, patient/*.cruds, system/*.rs)"));

        if (scopeRepository.existsByRoleIdAndScopeString(roleId, scopeString)) {
            throw new IllegalArgumentException(
                    "Scope '" + scopeString + "' is already assigned to role '" + role.getName() + "'.");
        }

        RoleScope rs = new RoleScope();
        rs.setRole(role);
        rs.setScopeString(scopeString);
        rs.setDescription(dto.getDescription());
        return scopeRepository.save(rs);
    }

    public void removeScope(Long roleId, Long scopeId) {
        RoleScope rs = scopeRepository.findById(scopeId)
                .orElseThrow(() -> new EntityNotFoundException("Scope not found: " + scopeId));
        if (!rs.getRole().getId().equals(roleId)) {
            throw new IllegalArgumentException("Scope " + scopeId + " does not belong to role " + roleId);
        }
        scopeRepository.delete(rs);
    }
}
