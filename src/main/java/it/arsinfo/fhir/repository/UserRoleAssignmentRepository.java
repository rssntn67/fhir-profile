package it.arsinfo.fhir.repository;

import it.arsinfo.fhir.domain.entity.UserRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, Long> {

    @Query("SELECT a FROM UserRoleAssignment a JOIN FETCH a.role r LEFT JOIN FETCH r.scopes " +
           "WHERE a.subject = :subject AND a.active = true")
    List<UserRoleAssignment> findActiveBySubject(@Param("subject") String subject);

    Optional<UserRoleAssignment> findBySubjectAndRoleIdAndActiveTrue(String subject, Long roleId);

    List<UserRoleAssignment> findBySubject(String subject);

    @Query("SELECT DISTINCT a.subject FROM UserRoleAssignment a WHERE a.active = true " +
           "ORDER BY a.subject")
    List<String> findDistinctActiveSubjects();
}
