package it.arsinfo.fhir.repository;

import it.arsinfo.fhir.domain.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.scopes WHERE r.id = :id")
    Optional<Role> findByIdWithScopes(@Param("id") Long id);

    @Query("SELECT DISTINCT r FROM Role r LEFT JOIN FETCH r.scopes WHERE r.name IN :names")
    List<Role> findByNameInWithScopes(@Param("names") List<String> names);
}
