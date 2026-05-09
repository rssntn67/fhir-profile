package it.arsinfo.fhir.web.api;

import it.arsinfo.fhir.service.UserRoleService;
import it.arsinfo.fhir.web.dto.UserRoleAssignmentDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users/{userId}/roles")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','CLINICAL_ADMIN')")
@Tag(name = "User Roles", description = "Assign RBAC roles to users by JWT subject")
public class UserRoleRestController {

    private final UserRoleService userRoleService;

    public UserRoleRestController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @GetMapping
    @Operation(summary = "List all role assignments for a user")
    public List<UserRoleAssignmentDto> listAssignments(@PathVariable String userId) {
        return userRoleService.findBySubject(userId).stream()
                .map(UserRoleAssignmentDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Assign a role to a user")
    public UserRoleAssignmentDto assignRole(@PathVariable String userId,
                                            @RequestParam Long roleId,
                                            Authentication authentication) {
        String assignedBy = authentication.getName();
        return UserRoleAssignmentDto.from(userRoleService.assignRole(userId, roleId, assignedBy));
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Revoke a role assignment")
    public ResponseEntity<Void> revokeRole(@PathVariable String userId,
                                            @PathVariable Long assignmentId) {
        userRoleService.revokeRole(assignmentId);
        return ResponseEntity.noContent().build();
    }
}
