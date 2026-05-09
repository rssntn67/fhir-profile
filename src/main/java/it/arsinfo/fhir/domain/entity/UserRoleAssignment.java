package it.arsinfo.fhir.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "fhir_user_role_assignments",
    uniqueConstraints = @UniqueConstraint(columnNames = {"subject", "role_id"})
)
@Getter @Setter @NoArgsConstructor
public class UserRoleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** JWT "sub" claim — the user's unique identity across the IdP. */
    @Column(nullable = false, length = 255)
    private String subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private Role role;

    /** Sub of the administrator who created the assignment. */
    @Column(name = "assigned_by", nullable = false, length = 255)
    private String assignedBy;

    @Column(nullable = false, updatable = false)
    private Instant assignedAt;

    /** When set, marks the assignment as revoked rather than deleting the row (audit trail). */
    @Column
    private Instant revokedAt;

    @Column(nullable = false)
    private boolean active = true;

    @PrePersist
    void onCreate() { assignedAt = Instant.now(); }
}
