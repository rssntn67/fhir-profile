package it.arsinfo.fhir.web.dto;

import java.time.Instant;

public class UserRoleAssignmentDto {

    private Long id;
    private String subject;
    private Long roleId;
    private String roleName;
    private String assignedBy;
    private Instant assignedAt;
    private Instant revokedAt;
    private boolean active;

    // ── getters / setters ──────────────────────────────────────────────────

    public Long getId()                    { return id; }
    public void setId(Long id)            { this.id = id; }

    public String getSubject()             { return subject; }
    public void setSubject(String v)      { this.subject = v; }

    public Long getRoleId()                { return roleId; }
    public void setRoleId(Long v)         { this.roleId = v; }

    public String getRoleName()            { return roleName; }
    public void setRoleName(String v)     { this.roleName = v; }

    public String getAssignedBy()          { return assignedBy; }
    public void setAssignedBy(String v)   { this.assignedBy = v; }

    public Instant getAssignedAt()         { return assignedAt; }
    public void setAssignedAt(Instant v)  { this.assignedAt = v; }

    public Instant getRevokedAt()          { return revokedAt; }
    public void setRevokedAt(Instant v)   { this.revokedAt = v; }

    public boolean isActive()              { return active; }
    public void setActive(boolean v)      { this.active = v; }

    public static UserRoleAssignmentDto from(it.arsinfo.fhir.domain.entity.UserRoleAssignment a) {
        UserRoleAssignmentDto dto = new UserRoleAssignmentDto();
        dto.setId(a.getId());
        dto.setSubject(a.getSubject());
        dto.setRoleId(a.getRole().getId());
        dto.setRoleName(a.getRole().getName());
        dto.setAssignedBy(a.getAssignedBy());
        dto.setAssignedAt(a.getAssignedAt());
        dto.setRevokedAt(a.getRevokedAt());
        dto.setActive(a.isActive());
        return dto;
    }
}
