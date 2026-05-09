package it.arsinfo.fhir.web.api;

import it.arsinfo.fhir.service.RoleService;
import it.arsinfo.fhir.web.dto.RoleDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/roles")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','CLINICAL_ADMIN')")
@Tag(name = "Roles", description = "RBAC role management")
public class RoleRestController {

    private final RoleService roleService;

    public RoleRestController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @Operation(summary = "List all roles")
    public List<RoleDto> listRoles() {
        return roleService.findAll().stream().map(RoleDto::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a role by ID")
    public RoleDto getRole(@PathVariable Long id) {
        return RoleDto.from(roleService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new role")
    public RoleDto createRole(@Valid @RequestBody RoleDto dto) {
        return RoleDto.from(roleService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing role")
    public RoleDto updateRole(@PathVariable Long id, @Valid @RequestBody RoleDto dto) {
        return RoleDto.from(roleService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete a role (built-in roles cannot be deleted)")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
