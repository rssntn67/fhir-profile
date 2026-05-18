package it.arsinfo.fhir.web.ui;

import it.arsinfo.fhir.service.RoleService;
import it.arsinfo.fhir.service.UserRoleService;
import it.arsinfo.fhir.web.dto.RoleDto;
import it.arsinfo.fhir.web.dto.UserRoleAssignmentDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','CLINICAL_ADMIN')")
public class AdminUsersController {

    private final UserRoleService userRoleService;
    private final RoleService     roleService;

    public AdminUsersController(UserRoleService userRoleService, RoleService roleService) {
        this.userRoleService = userRoleService;
        this.roleService     = roleService;
    }

    @GetMapping
    public String listUsers(Model model) {
        List<String> subjects = userRoleService.findAllSubjects();
        model.addAttribute("subjects", subjects);
        return "admin/users/list";
    }

    @GetMapping("/search")
    public String searchUser(@RequestParam String subject) {
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
