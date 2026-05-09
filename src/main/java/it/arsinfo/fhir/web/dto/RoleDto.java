package it.arsinfo.fhir.web.dto;

import it.arsinfo.fhir.security.jwt.SmartContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class RoleDto {

    private Long id;

    @NotBlank
    @Size(min = 2, max = 100)
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Name must be uppercase letters, digits or underscores")
    private String name;

    @Size(max = 500)
    private String description;

    private boolean builtIn;
    private SmartContext smartContext;
    private Instant createdAt;
    private Instant updatedAt;
    private List<RoleScopeDto> scopes;
    private int scopeCount;

    // ── getters / setters ──────────────────────────────────────────────────

    public Long getId()                          { return id; }
    public void setId(Long id)                  { this.id = id; }

    public String getName()                      { return name; }
    public void setName(String name)            { this.name = name; }

    public String getDescription()              { return description; }
    public void setDescription(String desc)     { this.description = desc; }

    public boolean isBuiltIn()                  { return builtIn; }
    public void setBuiltIn(boolean builtIn)     { this.builtIn = builtIn; }

    public SmartContext getSmartContext()        { return smartContext; }
    public void setSmartContext(SmartContext ctx){ this.smartContext = ctx; }

    public Instant getCreatedAt()               { return createdAt; }
    public void setCreatedAt(Instant v)         { this.createdAt = v; }

    public Instant getUpdatedAt()               { return updatedAt; }
    public void setUpdatedAt(Instant v)         { this.updatedAt = v; }

    public List<RoleScopeDto> getScopes()       { return scopes; }
    public void setScopes(List<RoleScopeDto> s) { this.scopes = s; }

    public int getScopeCount()                  { return scopeCount; }
    public void setScopeCount(int n)            { this.scopeCount = n; }

    /** Convenience factory — maps Role entity to DTO. */
    public static RoleDto from(it.arsinfo.fhir.domain.entity.Role role) {
        RoleDto dto = new RoleDto();
        dto.setId(role.getId());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        dto.setBuiltIn(role.isBuiltIn());
        dto.setSmartContext(role.getSmartContext());
        dto.setCreatedAt(role.getCreatedAt());
        dto.setUpdatedAt(role.getUpdatedAt());
        dto.setScopeCount(role.getScopes() != null ? role.getScopes().size() : 0);
        return dto;
    }
}
