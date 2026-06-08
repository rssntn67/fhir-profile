package it.arsinfo.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.config.r4.JpaR4Config;
import it.arsinfo.fhir.interceptor.SmartAuthorizationInterceptor;
import it.arsinfo.fhir.servlet.FhirRestfulServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

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
