package it.arsinfo.fhir.service;

import it.arsinfo.fhir.domain.entity.Role;
import it.arsinfo.fhir.domain.entity.UserRoleAssignment;
import it.arsinfo.fhir.repository.RoleRepository;
import it.arsinfo.fhir.repository.UserRoleAssignmentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class UserRoleService {

    private final UserRoleAssignmentRepository assignmentRepository;
    private final RoleRepository roleRepository;

    public UserRoleService(UserRoleAssignmentRepository assignmentRepository,
                           RoleRepository roleRepository) {
        this.assignmentRepository = assignmentRepository;
        this.roleRepository       = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<UserRoleAssignment> findBySubject(String subject) {
        return assignmentRepository.findBySubject(subject);
    }

    @Transactional(readOnly = true)
    public List<UserRoleAssignment> findActiveBySubject(String subject) {
        return assignmentRepository.findActiveBySubject(subject);
    }

    @Transactional(readOnly = true)
    public List<String> findAllSubjects() {
        return assignmentRepository.findDistinctActiveSubjects();
    }

    public UserRoleAssignment assignRole(String subject, Long roleId, String assignedBy) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleId));

        // Re-activate if previously revoked
        return assignmentRepository
                .findBySubjectAndRoleIdAndActiveTrue(subject, roleId)
                .orElseGet(() -> {
                    UserRoleAssignment a = new UserRoleAssignment();
                    a.setSubject(subject);
                    a.setRole(role);
                    a.setAssignedBy(assignedBy);
                    return assignmentRepository.save(a);
                });
    }

    public void revokeRole(Long assignmentId) {
        UserRoleAssignment a = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException("Assignment not found: " + assignmentId));
        a.setActive(false);
        a.setRevokedAt(Instant.now());
        assignmentRepository.save(a);
    }
}
