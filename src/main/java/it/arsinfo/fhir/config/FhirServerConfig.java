package it.arsinfo.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.config.r4.JpaR4Config;
import ca.uhn.fhir.jpa.config.util.HapiEntityManagerFactoryUtil;
import ca.uhn.fhir.jpa.model.config.PartitionSettings;
import it.arsinfo.fhir.interceptor.SmartAuthorizationInterceptor;
import it.arsinfo.fhir.servlet.FhirRestfulServlet;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

@Configuration
@Import(JpaR4Config.class)
public class FhirServerConfig {

    // FhirContext bean is provided as @Primary by HAPI's FhirContextR4Config (imported via JpaR4Config).
    // Do NOT declare a second one here — FhirContext is expensive to create and must be a true singleton.

    /**
     * JPA storage settings — replaces the old DaoConfig from HAPI < 7.x.
     * Full-text/Hibernate Search disabled (SQL-only). Reasonable fetch limits set.
     */
    @Bean
    public JpaStorageSettings jpaStorageSettings() {
        JpaStorageSettings settings = new JpaStorageSettings();
        settings.setHibernateSearchIndexFullText(false);
        // Allow conditional deletes (e.g. DELETE /Patient?family=Test) — needed for admin operations
        settings.setAllowMultipleDelete(true);
        settings.setFetchSizeDefaultMaximum(200);
        settings.setResourceServerIdStrategy(JpaStorageSettings.IdStrategyEnum.SEQUENTIAL_NUMERIC);
        return settings;
    }

    /**
     * Custom entity manager factory that uses HAPI's persistence provider (required for
     * HAPI's custom Hibernate services such as ISequenceValueMassager) and also scans
     * our RBAC entity package so that Spring Data JPA repositories can be bootstrapped
     * against a single, correctly configured persistence unit.
     *
     * This bean is marked @Primary to take precedence over Spring Boot's
     * HibernateJpaAutoConfiguration-created factory. We inject PartitionSettings
     * explicitly to ensure HAPI's JpaConfig has been fully initialized before we
     * call HapiEntityManagerFactoryUtil.
     */
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            ConfigurableListableBeanFactory beanFactory,
            FhirContext fhirContext,
            JpaStorageSettings jpaStorageSettings,
            @SuppressWarnings("unused") PartitionSettings partitionSettings) {

        LocalContainerEntityManagerFactoryBean emf =
                HapiEntityManagerFactoryUtil.newEntityManagerFactory(beanFactory, fhirContext, jpaStorageSettings);

        // Add our RBAC entities to the packages already scanned by HAPI
        // (ca.uhn.fhir.jpa.model.entity, ca.uhn.fhir.jpa.entity)
        emf.setPackagesToScan(
                "ca.uhn.fhir.jpa.model.entity",
                "ca.uhn.fhir.jpa.entity",
                "it.arsinfo.fhir.domain.entity"
        );
        return emf;
    }

    /**
     * Registers the HAPI FHIR servlet at /fhir/*.
     * Spring Security filter chains run before it and populate SecurityContextHolder with the JWT.
     */
    @Bean
    public ServletRegistrationBean<FhirRestfulServlet> fhirServletRegistration(
            FhirContext fhirContext,
            SmartAuthorizationInterceptor authorizationInterceptor,
            ApplicationContext applicationContext,
            Environment environment) {

        FhirRestfulServlet servlet = new FhirRestfulServlet(
                fhirContext, authorizationInterceptor, applicationContext, environment);

        ServletRegistrationBean<FhirRestfulServlet> registration =
                new ServletRegistrationBean<>(servlet, "/fhir/*");
        registration.setName("FhirServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
