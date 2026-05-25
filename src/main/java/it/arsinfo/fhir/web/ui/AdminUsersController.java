package it.arsinfo.fhir.web.ui;

import it.arsinfo.fhir.service.RoleService;
import it.arsinfo.fhir.service.UserRoleService;
import it.arsinfo.fhir.web.dto.RoleDto;
import it.arsinfo.fhir.web.dto.UserRoleAssignmentDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.regex.Pattern;


@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','CLINICAL_ADMIN')")
public class AdminUsersController {

    // UUID, email, and common IdP subject formats — no path separators or control chars
    private static final Pattern SAFE_SUBJECT = Pattern.compile("[a-zA-Z0-9._@+\\-]{1,256}");

    private final UserRoleService userRoleService;
    private final RoleService     roleService;

    public AdminUsersController(UserRoleService userRoleService, RoleService roleService) {
        this.userRoleService = userRoleService;
        this.roleService     = roleService;
    }

    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("userRoles", userRoleService.findAllSubjectsWithRoles());
        return "admin/users/list";
    }

    @GetMapping("/search")
    public String searchUser(@RequestParam String subject) {
        if (!SAFE_SUBJECT.matcher(subject).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid subject identifier");
        }
        return "redirect:/admin/users/" + subject + "/roles";
    }

    @GetMapping("/{userId}/roles")
    public String manageUserRoles(@PathVariable String userId, Model model) {
        model.addAttribute("userId", userId);
        model.addAttribute("assignments", userRoleService.findActiveBySubject(userId).stream()
                .map(UserRoleAssignmentDto::from).toList());
        model.addAttribute("allRoles", roleService.findAll().stream().map(RoleDto::from).toList());
        return "admin/users/assignments";
    }

    @PostMapping("/{userId}/roles")
    public String assignRole(@PathVariable String userId,
                             @RequestParam Long roleId,
                             Authentication authentication,
                             RedirectAttributes attrs) {
        try {
            userRoleService.assignRole(userId, roleId, authentication.getName());
            attrs.addFlashAttribute("successMessage", "Role assigned.");
        } catch (Exception e) {
            attrs.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users/" + userId + "/roles";
    }

    @PostMapping("/{userId}/roles/{assignmentId}/revoke")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String revokeRole(@PathVariable String userId,
                             @PathVariable Long assignmentId,
                             RedirectAttributes attrs) {
        try {
            userRoleService.revokeRole(assignmentId);
            attrs.addFlashAttribute("successMessage", "Role revoked.");
        } catch (Exception e) {
            attrs.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users/" + userId + "/roles";
    }
}
