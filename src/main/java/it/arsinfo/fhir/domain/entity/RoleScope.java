package it.arsinfo.fhir.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "fhir_role_scopes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"role_id", "scope_string"})
)
@Getter @Setter @NoArgsConstructor
public class RoleScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id")
    private Role role;

    /** SMART scope string, e.g. "user/Patient.cruds" or "patient/Observation.read". */
    @Column(name = "scope_string", nullable = false, length = 200)
    private String scopeString;

    @Column(length = 500)
    private String description;
}
