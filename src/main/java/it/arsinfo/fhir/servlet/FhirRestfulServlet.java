package it.arsinfo.fhir.servlet;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.provider.JpaSystemProvider;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import it.arsinfo.fhir.interceptor.SmartAuthorizationInterceptor;
import jakarta.servlet.ServletException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * HAPI FHIR RESTful servlet.
 *
 * Registers resource providers discovered from the Spring ApplicationContext
 * and installs the SMART authorization interceptor.
 */
public class FhirRestfulServlet extends RestfulServer {

    private final SmartAuthorizationInterceptor authorizationInterceptor;
    private final ApplicationContext applicationContext;
    private final Environment environment;

    public FhirRestfulServlet(FhirContext fhirContext,
                              SmartAuthorizationInterceptor authorizationInterceptor,
                              ApplicationContext applicationContext,
                              Environment environment) {
        super(fhirContext);
        this.authorizationInterceptor = authorizationInterceptor;
        this.applicationContext       = applicationContext;
        this.environment              = environment;
    }

    @Override
    protected void initialize() throws ServletException {
        setDefaultPrettyPrint(true);
        setDefaultResponseEncoding(ca.uhn.fhir.rest.api.EncodingEnum.JSON);

        // Register all IResourceProvider beans from Spring context
        registerProviders(applicationContext.getBeansOfType(IResourceProvider.class).values());

        // System provider handles $everything, _history, $validate, batch, transaction
        registerProvider(applicationContext.getBean(JpaSystemProvider.class));

        // Request/response logging
        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
        loggingInterceptor.setLoggerName("fhir.access");
        registerInterceptor(loggingInterceptor);

        // SMART RBAC enforcement — registered before response highlighter
        registerInterceptor(authorizationInterceptor);

        // Pretty HTML output in browser (dev only)
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        if (isDev) {
            registerInterceptor(new ResponseHighlighterInterceptor());
        }
    }
}
