package it.arsinfo.fhir.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.batch2.jobs.config.Batch2JobsConfig;
import ca.uhn.fhir.jpa.api.config.ThreadPoolFactoryConfig;
import ca.uhn.fhir.jpa.batch2.JpaBatch2Config;
import ca.uhn.fhir.jpa.config.HapiJpaConfig;
import ca.uhn.fhir.jpa.config.r4.JpaR4Config;
import ca.uhn.fhir.jpa.model.config.SubscriptionSettings;
import ca.uhn.fhir.jpa.subscription.channel.config.SubscriptionChannelConfig;
import ca.uhn.fhir.jpa.subscription.submit.config.SubscriptionSubmitterConfig;
import ca.uhn.fhir.jpa.config.util.HapiEntityManagerFactoryUtil;
import ca.uhn.fhir.jpa.model.config.PartitionSettings;
import it.arsinfo.fhir.interceptor.SmartAuthorizationInterceptor;
import it.arsinfo.fhir.servlet.FhirRestfulServlet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.util.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;

@Configuration
@Import({JpaR4Config.class, HapiJpaConfig.class, JpaBatch2Config.class, Batch2JobsConfig.class,
        SubscriptionChannelConfig.class, ThreadPoolFactoryConfig.class,
        SubscriptionSubmitterConfig.class})
public class FhirServerConfig {

    // FhirContext bean is provided as @Primary by HAPI's FhirContextR4Config (imported via JpaR4Config).
    // Do NOT declare a second one here — FhirContext is expensive to create and must be a true singleton.

    /**
     * Partition settings — required by HapiEntityManagerFactoryUtil and HAPI's JpaConfig beans.
     * Partitioning is disabled by default (single-tenant deployment).
     */
    @Bean
    public PartitionSettings partitionSettings() {
        return new PartitionSettings();
    }

    @Bean
    public SubscriptionSettings subscriptionSettings() {
        return new SubscriptionSettings();
    }

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
            DataSource dataSource,
            FhirContext fhirContext,
            JpaStorageSettings jpaStorageSettings,
            JpaProperties jpaProperties,
            @Value("${spring.jpa.hibernate.ddl-auto:none}") String ddlAuto,
            @SuppressWarnings("unused") PartitionSettings partitionSettings) {

        LocalContainerEntityManagerFactoryBean emf =
                HapiEntityManagerFactoryUtil.newEntityManagerFactory(beanFactory, fhirContext, jpaStorageSettings);

        // Wire in the Spring Boot auto-configured DataSource (H2 in dev, PostgreSQL in prod)
        emf.setDataSource(dataSource);

        // Add our RBAC entities to the packages already scanned by HAPI
        // (ca.uhn.fhir.jpa.model.entity, ca.uhn.fhir.jpa.entity)
        emf.setPackagesToScan(
                "ca.uhn.fhir.jpa.model.entity",
                "ca.uhn.fhir.jpa.entity",
                "it.arsinfo.fhir.domain.entity"
        );

        // Both hibernate-search-backend-elasticsearch and hibernate-search-backend-lucene
        // are on the classpath (pulled in by hapi-fhir-jpaserver-base). Hibernate Search's
        // bootstrap fails with HSEARCH000582 if neither backend is explicitly selected.
        // We are SQL-only (full-text search disabled via JpaStorageSettings), so disable
        // the Hibernate Search integration entirely.
        emf.getJpaPropertyMap().put("hibernate.search.enabled", "false");

        // Propagate spring.jpa.properties.* (e.g. hibernate.dialect) into the HAPI EMF.
        // Spring Boot's JPA auto-configuration does not touch our custom EMF, so we merge
        // manually. HibernatePropertiesProvider reads hibernate.dialect from this map.
        emf.getJpaPropertyMap().putAll(jpaProperties.getProperties());

        // Apply spring.jpa.hibernate.ddl-auto to our HAPI EMF. Spring Boot's JPA
        // auto-config does not touch our custom EMF, so we apply the setting manually.
        // Set both the Hibernate legacy property and JPA 3.x standard property.
        if (StringUtils.hasText(ddlAuto) && !"none".equals(ddlAuto)) {
            emf.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", ddlAuto);
            String jpaAction = "create-drop".equals(ddlAuto) ? "drop-and-create" : ddlAuto;
            emf.getJpaPropertyMap().put("jakarta.persistence.schema-generation.database.action", jpaAction);
        }
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
