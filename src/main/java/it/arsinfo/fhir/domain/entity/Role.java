package it.arsinfo.fhir.domain.entity;

import it.arsinfo.fhir.security.jwt.SmartContext;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "fhir_roles")
@Getter @Setter @NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /** True for the six built-in roles that ship with the server and cannot be deleted. */
    @Column(nullable = false)
    private boolean builtIn = false;

    /** Dominant SMART context for this role (informational; actual access is driven by scopes). */
    @Enumerated(EnumType.STRING)
    @Column(name = "smart_context", length = 20)
    private SmartContext smartContext;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<RoleScope> scopes = new HashSet<>();

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}
