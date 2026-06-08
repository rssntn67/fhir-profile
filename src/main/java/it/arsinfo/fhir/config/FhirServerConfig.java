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

    /** Shared FHIR R4 context — thread-safe, expensive to create, must be a singleton. */
    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    /**
     * JPA storage settings — replaces the old DaoConfig from HAPI < 7.x.
     * Full-text/Hibernate Search disabled (SQL-only). Reasonable fetch limits set.
     */
    @Bean
    public JpaStorageSettings jpaStorageSettings() {
        JpaStorageSettings settings = new JpaStorageSettings();
        // Disable Hibernate Search / Lucene full-text indexing; use SQL search only
        settings.setHibernateSearchIndexFullText(false);
        settings.setAllowMultipleDelete(true);
        // Cap results returned per query
        settings.setFetchSizeDefaultMaximum(200);
        settings.setResourceServerIdStrategy(JpaStorageSettings.IdStrategyEnum.SEQUENTIAL_NUMERIC);
        return settings;
    }

    /**
     * Registers the HAPI FHIR servlet at /fhir/*.
     */
    @Bean
    public ServletRegistrationBean<FhirRestfulServlet> fhirServletRegistration(
            SmartAuthorizationInterceptor authorizationInterceptor,
            ApplicationContext applicationContext,
            Environment environment) {

        FhirRestfulServlet servlet = new FhirRestfulServlet(
                authorizationInterceptor, applicationContext, environment);

        ServletRegistrationBean<FhirRestfulServlet> registration =
                new ServletRegistrationBean<>(servlet, "/fhir/*");
        registration.setName("FhirServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
