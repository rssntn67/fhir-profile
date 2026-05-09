package it.arsinfo.fhir.service;

import it.arsinfo.fhir.domain.entity.Role;
import it.arsinfo.fhir.repository.RoleRepository;
import it.arsinfo.fhir.web.dto.RoleDto;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Role findById(Long id) {
        return roleRepository.findByIdWithScopes(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));
    }

    public Role create(RoleDto dto) {
        if (roleRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("Role already exists: " + dto.getName());
        }
        Role role = new Role();
        role.setName(dto.getName().trim().toUpperCase());
        role.setDescription(dto.getDescription());
        role.setSmartContext(dto.getSmartContext());
        return roleRepository.save(role);
    }

    public Role update(Long id, RoleDto dto) {
        Role role = findById(id);
        if (role.isBuiltIn()) {
            // Allow description update but not name change for built-in roles
            role.setDescription(dto.getDescription());
        } else {
            role.setDescription(dto.getDescription());
            if (dto.getSmartContext() != null) {
                role.setSmartContext(dto.getSmartContext());
            }
        }
        return roleRepository.save(role);
    }

    public void delete(Long id) {
        Role role = findById(id);
        if (role.isBuiltIn()) {
            throw new IllegalStateException("Built-in role '" + role.getName() + "' cannot be deleted.");
        }
        roleRepository.delete(role);
    }
}
