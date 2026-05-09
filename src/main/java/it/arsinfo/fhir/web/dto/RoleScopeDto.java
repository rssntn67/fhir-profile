package it.arsinfo.fhir.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RoleScopeDto {

    private Long id;
    private Long roleId;

    @NotBlank
    @Size(max = 200)
    private String scopeString;

    @Size(max = 500)
    private String description;

    // ── getters / setters ──────────────────────────────────────────────────

    public Long getId()                         { return id; }
    public void setId(Long id)                  { this.id = id; }

    public Long getRoleId()                     { return roleId; }
    public void setRoleId(Long roleId)          { this.roleId = roleId; }

    public String getScopeString()              { return scopeString; }
    public void setScopeString(String v)        { this.scopeString = v; }

    public String getDescription()              { return description; }
    public void setDescription(String v)        { this.description = v; }

    public static RoleScopeDto from(it.arsinfo.fhir.domain.entity.RoleScope rs) {
        RoleScopeDto dto = new RoleScopeDto();
        dto.setId(rs.getId());
        dto.setRoleId(rs.getRole().getId());
        dto.setScopeString(rs.getScopeString());
        dto.setDescription(rs.getDescription());
        return dto;
    }
}
