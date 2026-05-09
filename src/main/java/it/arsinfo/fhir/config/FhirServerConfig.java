package it.arsinfo.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import it.arsinfo.fhir.interceptor.SmartAuthorizationInterceptor;
import it.arsinfo.fhir.servlet.FhirRestfulServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class FhirServerConfig {

    /** Shared FHIR R4 context — thread-safe, expensive to create, must be a singleton. */
    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    /**
     * Registers the HAPI FHIR servlet at /fhir/*.
     * All FHIR operations go through this servlet; Spring Security filter chains
     * run before it and populate the SecurityContextHolder with the JWT.
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
