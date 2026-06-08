package it.arsinfo.fhir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// @EntityScan is intentionally omitted: entity packages are declared via
// FhirServerConfig.entityManagerFactory() → setPackagesToScan() so that
// both HAPI entities and our RBAC entities share the same HAPI-configured EMF.
//
// @EnableJpaRepositories for ca.uhn.fhir.jpa.dao.data is handled by HAPI's JpaConfig
// (using EnversRevisionRepositoryFactoryBean). We only add our own package here,
// pointing to the shared entityManagerFactory bean.
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaRepositories(
    basePackages = "it.arsinfo.fhir.repository",
    entityManagerFactoryRef = "entityManagerFactory"
)
public class FhirServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FhirServerApplication.class, args);
    }
}
