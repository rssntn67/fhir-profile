package it.arsinfo.fhir.init;

import it.arsinfo.fhir.domain.entity.Role;
import it.arsinfo.fhir.domain.entity.RoleScope;
import it.arsinfo.fhir.domain.enums.BuiltInRole;
import it.arsinfo.fhir.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the six built-in roles on first startup.
 * Idempotent: roles that already exist in the DB are skipped.
 */
@Component
public class BuiltInRoleSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BuiltInRoleSeeder.class);

    private final RoleRepository roleRepository;

    public BuiltInRoleSeeder(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (BuiltInRole builtIn : BuiltInRole.values()) {
            if (roleRepository.existsByName(builtIn.name())) {
                log.debug("Built-in role '{}' already exists — skipping", builtIn.name());
                continue;
            }
            Role role = new Role();
            role.setName(builtIn.name());
            role.setDescription(builtIn.description);
            role.setBuiltIn(true);
            role.setSmartContext(builtIn.smartContext);

            for (String scopeString : builtIn.scopeStrings) {
                RoleScope rs = new RoleScope();
                rs.setRole(role);
                rs.setScopeString(scopeString);
                role.getScopes().add(rs);
            }

            roleRepository.save(role);
            log.info("Seeded built-in role '{}' with {} scopes", builtIn.name(), builtIn.scopeStrings.size());
        }
    }
}
