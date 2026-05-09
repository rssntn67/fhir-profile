package it.arsinfo.fhir.repository;

import it.arsinfo.fhir.domain.entity.RoleScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleScopeRepository extends JpaRepository<RoleScope, Long> {

    List<RoleScope> findByRoleId(Long roleId);

    boolean existsByRoleIdAndScopeString(Long roleId, String scopeString);

    void deleteByRoleId(Long roleId);
}
