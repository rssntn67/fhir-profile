package it.arsinfo.fhir.web.api;

import it.arsinfo.fhir.service.RoleScopeService;
import it.arsinfo.fhir.web.dto.RoleScopeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/roles/{roleId}/scopes")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','CLINICAL_ADMIN')")
@Tag(name = "Role Scopes", description = "SMART scope assignment to roles")
public class RoleScopeRestController {

    private final RoleScopeService roleScopeService;

    public RoleScopeRestController(RoleScopeService roleScopeService) {
        this.roleScopeService = roleScopeService;
    }

    @GetMapping
    @Operation(summary = "List scopes for a role")
    public List<RoleScopeDto> listScopes(@PathVariable Long roleId) {
        return roleScopeService.findByRole(roleId).stream().map(RoleScopeDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a SMART scope to a role")
    public RoleScopeDto addScope(@PathVariable Long roleId, @Valid @RequestBody RoleScopeDto dto) {
        return RoleScopeDto.from(roleScopeService.addScope(roleId, dto));
    }

    @DeleteMapping("/{scopeId}")
    @Operation(summary = "Remove a scope from a role")
    public ResponseEntity<Void> removeScope(@PathVariable Long roleId, @PathVariable Long scopeId) {
        roleScopeService.removeScope(roleId, scopeId);
        return ResponseEntity.noContent().build();
    }
}
