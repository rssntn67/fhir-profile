package it.arsinfo.fhir.web.ui;

import it.arsinfo.fhir.security.jwt.SmartContext;
import it.arsinfo.fhir.service.RoleService;
import it.arsinfo.fhir.service.RoleScopeService;
import it.arsinfo.fhir.web.dto.RoleDto;
import it.arsinfo.fhir.web.dto.RoleScopeDto;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin/roles")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','CLINICAL_ADMIN')")
public class AdminRolesController {

    private static final List<String> SCOPE_SUGGESTIONS = buildScopeSuggestions();

    private static List<String> buildScopeSuggestions() {
        String[] contexts = {"patient", "user", "system"};
        String[] resources = {
            "*",
            "Patient", "Practitioner", "PractitionerRole", "Organization", "Location",
            "Observation", "Condition", "Procedure", "MedicationRequest", "MedicationStatement",
            "DiagnosticReport", "Encounter", "AllergyIntolerance", "DocumentReference",
            "ServiceRequest", "ImagingStudy", "CarePlan", "CareTeam", "Goal",
            "Device", "RelatedPerson", "Coverage"
        };
        String[] perms = {"read", "write", "cruds"};
        List<String> result = new ArrayList<>();
        for (String ctx : contexts) {
            for (String res : resources) {
                for (String perm : perms) {
                    result.add(ctx + "/" + res + "." + perm);
                }
            }
        }
        return List.copyOf(result);
    }

    private final RoleService roleService;
    private final RoleScopeService roleScopeService;

    public AdminRolesController(RoleService roleService, RoleScopeService roleScopeService) {
        this.roleService      = roleService;
        this.roleScopeService = roleScopeService;
    }

    @GetMapping
    public String listRoles(Model model) {
        model.addAttribute("roles", roleService.findAll().stream().map(RoleDto::from).toList());
        return "admin/roles/list";
    }

    @GetMapping("/new")
    public String newRoleForm(Model model) {
        model.addAttribute("roleDto", new RoleDto());
        model.addAttribute("contexts", SmartContext.values());
        return "admin/roles/create";
    }

    @PostMapping
    public String createRole(@Valid @ModelAttribute("roleDto") RoleDto dto,
                             BindingResult result, Model model,
                             RedirectAttributes attrs) {
        if (result.hasErrors()) {
            model.addAttribute("contexts", SmartContext.values());
            return "admin/roles/create";
        }
        try {
            roleService.create(dto);
            attrs.addFlashAttribute("successMessage", "Role '" + dto.getName() + "' created.");
            return "redirect:/admin/roles";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("contexts", SmartContext.values());
            return "admin/roles/create";
        }
    }

    @GetMapping("/{id}/edit")
    public String editRoleForm(@PathVariable Long id, Model model) {
        model.addAttribute("roleDto", RoleDto.from(roleService.findById(id)));
        model.addAttribute("contexts", SmartContext.values());
        return "admin/roles/edit";
    }

    @PostMapping("/{id}")
    public String updateRole(@PathVariable Long id,
                             @Valid @ModelAttribute("roleDto") RoleDto dto,
                             BindingResult result, Model model,
                             RedirectAttributes attrs) {
        if (result.hasErrors()) {
            model.addAttribute("contexts", SmartContext.values());
            return "admin/roles/edit";
        }
        roleService.update(id, dto);
        attrs.addFlashAttribute("successMessage", "Role updated.");
        return "redirect:/admin/roles";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public String deleteRole(@PathVariable Long id, RedirectAttributes attrs) {
        try {
            roleService.delete(id);
            attrs.addFlashAttribute("successMessage", "Role deleted.");
        } catch (IllegalStateException e) {
            attrs.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/roles";
    }

    // ── Scope management ─────────────────────────────────────────────────────

    @GetMapping("/{id}/scopes")
    public String manageScopesForm(@PathVariable Long id, Model model) {
        model.addAttribute("role", RoleDto.from(roleService.findById(id)));
        model.addAttribute("scopes", roleScopeService.findByRole(id).stream()
                .map(RoleScopeDto::from).toList());
        model.addAttribute("newScope", new RoleScopeDto());
        model.addAttribute("scopeSuggestions", SCOPE_SUGGESTIONS);
        return "admin/roles/scopes";
    }

    @PostMapping("/{id}/scopes")
    public String addScope(@PathVariable Long id,
                           @ModelAttribute("newScope") RoleScopeDto dto,
                           RedirectAttributes attrs) {
        try {
            roleScopeService.addScope(id, dto);
            attrs.addFlashAttribute("successMessage", "Scope '" + dto.getScopeString() + "' added.");
        } catch (IllegalArgumentException e) {
            attrs.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/roles/" + id + "/scopes";
    }

    @PostMapping("/{id}/scopes/{scopeId}/delete")
    public String removeScope(@PathVariable Long id, @PathVariable Long scopeId,
                              RedirectAttributes attrs) {
        try {
            roleScopeService.removeScope(id, scopeId);
            attrs.addFlashAttribute("successMessage", "Scope removed.");
        } catch (Exception e) {
            attrs.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/roles/" + id + "/scopes";
    }
}
