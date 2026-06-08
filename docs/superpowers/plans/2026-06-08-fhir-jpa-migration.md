# HAPI FHIR JPA Server Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the two in-memory FHIR providers with `hapi-fhir-jpaserver-base` so all R4 resources are persisted in PostgreSQL, while preserving the SMART RBAC auth layer and migrating Liquibase to Flyway.

**Architecture:** Add `hapi-fhir-jpaserver-base` to the existing Spring Boot project. HAPI's JPA DAOs auto-register as `IResourceProvider` beans via an `@Import`-ed Spring config class, replacing the `ConcurrentHashMap` providers. A single shared `EntityManagerFactory` scans both HAPI's entity packages and our RBAC entities. A new `V1__create_rbac_tables.sql` Flyway migration replaces the Liquibase XML changeset.

**Tech Stack:** Java 17, Spring Boot 3.3.5, HAPI FHIR 8.0.0 (`hapi-fhir-jpaserver-base`), Flyway 10, PostgreSQL 15, H2 (dev), JUnit 5, AssertJ.

---

## File Map

| Status | Path | Change |
|---|---|---|
| Modify | `pom.xml` | Swap deps |
| Modify | `src/main/java/it/arsinfo/fhir/FhirServerApplication.java` | Add `@EntityScan` |
| Modify | `src/main/java/it/arsinfo/fhir/config/FhirServerConfig.java` | Add `@Import`, `JpaStorageSettings` |
| Modify | `src/main/java/it/arsinfo/fhir/servlet/FhirRestfulServlet.java` | Register system provider |
| Modify | `src/main/resources/application.yml` | Remove Liquibase config |
| Modify | `src/main/resources/application-dev.yml` | Switch to `create-drop`, disable Flyway |
| Modify | `src/main/resources/application-prod.yml` | Switch `ddl-auto: none`, enable Flyway |
| Create | `src/main/resources/db/migration/V1__create_rbac_tables.sql` | Converted RBAC schema |
| Create | `src/main/java/it/arsinfo/fhir/init/FhirDataSeeder.java` | Dev-only test data seeder |
| Delete | `src/main/java/it/arsinfo/fhir/provider/InMemoryPatientProvider.java` | |
| Delete | `src/main/java/it/arsinfo/fhir/provider/InMemoryObservationProvider.java` | |
| Delete | `src/main/resources/db/changelog/` | Entire directory |
| Modify | `src/main/java/it/arsinfo/fhir/interceptor/SmartRuleBuilder.java` | Add three new rule methods |
| Modify | `src/test/java/it/arsinfo/fhir/interceptor/SmartRuleBuilderTest.java` | Three new test cases |
| Create | `src/test/java/it/arsinfo/fhir/FhirJpaIT.java` | End-to-end JPA integration test (RANDOM_PORT) |
| Create | `src/test/java/it/arsinfo/fhir/init/FhirDataSeederTest.java` | Dev seeder unit test |

---

## Task 1: Update Maven Dependencies

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Replace HAPI plain server deps with jpaserver-base**

In `pom.xml`, remove these three blocks:

```xml
<!-- REMOVE these three -->
<dependency>
  <groupId>ca.uhn.hapi.fhir</groupId>
  <artifactId>hapi-fhir-server</artifactId>
  <version>${hapi-fhir.version}</version>
</dependency>
<dependency>
  <groupId>ca.uhn.hapi.fhir</groupId>
  <artifactId>hapi-fhir-structures-r4</artifactId>
  <version>${hapi-fhir.version}</version>
</dependency>
<dependency>
  <groupId>ca.uhn.hapi.fhir</groupId>
  <artifactId>hapi-fhir-validation-resources-r4</artifactId>
  <version>${hapi-fhir.version}</version>
</dependency>
```

Add in their place:

```xml
<!-- ADD: full JPA server (pulls in structures-r4, validation-resources-r4 transitively) -->
<dependency>
  <groupId>ca.uhn.hapi.fhir</groupId>
  <artifactId>hapi-fhir-jpaserver-base</artifactId>
  <version>${hapi-fhir.version}</version>
</dependency>
```

- [ ] **Step 2: Replace Liquibase with Flyway**

Remove:
```xml
<!-- REMOVE -->
<dependency>
  <groupId>org.liquibase</groupId>
  <artifactId>liquibase-core</artifactId>
</dependency>
```

Add:
```xml
<!-- ADD -->
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

- [ ] **Step 3: Verify compilation**

```bash
mvn compile -DskipTests 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`. If HAPI's `JpaR4Config` class is not found at the expected import path in Task 3, check the jar contents with: `jar tf ~/.m2/repository/ca/uhn/hapi/fhir/hapi-fhir-jpaserver-base/8.0.0/hapi-fhir-jpaserver-base-8.0.0.jar | grep JpaR4`

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "build: swap hapi-fhir-server for jpaserver-base, replace liquibase with flyway"
```

---

## Task 2: Flyway Migration — Convert RBAC Schema

**Files:**
- Create: `src/main/resources/db/migration/V1__create_rbac_tables.sql`
- Delete: `src/main/resources/db/changelog/` (entire directory)
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-dev.yml`
- Modify: `src/main/resources/application-prod.yml`

- [ ] **Step 1: Create the Flyway SQL migration**

Create `src/main/resources/db/migration/V1__create_rbac_tables.sql`:

```sql
-- RBAC schema: converted from db/changelog/001-create-role-tables.xml

CREATE TABLE fhir_roles (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100)             NOT NULL,
    description VARCHAR(500),
    built_in    BOOLEAN                  NOT NULL DEFAULT FALSE,
    smart_context VARCHAR(20),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_fhir_roles_name UNIQUE (name)
);

CREATE INDEX idx_fhir_roles_name ON fhir_roles (name);

CREATE TABLE fhir_role_scopes (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    role_id      BIGINT       NOT NULL,
    scope_string VARCHAR(200) NOT NULL,
    description  VARCHAR(500),
    CONSTRAINT fk_role_scope_role    FOREIGN KEY (role_id) REFERENCES fhir_roles(id),
    CONSTRAINT uq_role_scope         UNIQUE (role_id, scope_string)
);

CREATE INDEX idx_role_scopes_role_id ON fhir_role_scopes (role_id);

CREATE TABLE fhir_user_role_assignments (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    subject     VARCHAR(255)             NOT NULL,
    role_id     BIGINT                   NOT NULL,
    assigned_by VARCHAR(255)             NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    active      BOOLEAN                  NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_user_role_role        FOREIGN KEY (role_id) REFERENCES fhir_roles(id),
    CONSTRAINT uq_user_role_assignment  UNIQUE (subject, role_id)
);

CREATE INDEX idx_user_role_subject ON fhir_user_role_assignments (subject);
CREATE INDEX idx_user_role_active  ON fhir_user_role_assignments (subject, active);
```

- [ ] **Step 2: Remove Liquibase config from `application.yml`**

In `src/main/resources/application.yml`, remove the entire `liquibase:` block and the `flyway: enabled: false` block, and also remove the comment about flyway conflicting with liquibase. The `flyway:` key is not needed in the base file — it will be enabled per-profile. The final YAML should not contain any `liquibase:` or `flyway:` keys:

```yaml
spring:
  application:
    name: fhir-profile-server

  jpa:
    open-in-view: false
    properties:
      hibernate:
        format_sql: false

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${AAA_ISSUER_URI:http://localhost:8180/realms/fhir}
          jwk-set-uri: ${AAA_JWKS_URI:http://localhost:8180/realms/fhir/protocol/openid-connect/certs}
      client:
        registration:
          keycloak:
            client-id: fhir-server
            authorization-grant-type: authorization_code
            scope: openid,profile,email
            redirect-uri: "{baseUrl}/login/oauth2/code/keycloak"
        provider:
          keycloak:
            issuer-uri: ${AAA_ISSUER_URI:http://localhost:8180/realms/fhir}
            user-name-attribute: preferred_username

fhir:
  rbac:
    roles-claim: ${RBAC_ROLES_CLAIM:realm_access.roles}
    scope-claim: ${RBAC_SCOPE_CLAIM:scope}
    patient-claim: ${RBAC_PATIENT_CLAIM:patient}
    patient-claim-fallback: ${RBAC_PATIENT_CLAIM_FALLBACK:launch_context.patient}
    db-roles-enabled: ${RBAC_DB_ROLES_ENABLED:true}

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs
```

- [ ] **Step 3: Update `application-dev.yml`**

Replace the entire file content with:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:fhirdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:

  h2:
    console:
      enabled: true
      path: /h2-console

  flyway:
    enabled: false   # H2 schema managed by ddl-auto=create-drop

  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop
    show-sql: false

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics

logging:
  level:
    it.arsinfo.fhir: DEBUG
    ca.uhn.fhir: INFO
    org.springframework.security: DEBUG
    org.flywaydb: INFO
```

- [ ] **Step 4: Update `application-prod.yml`**

Replace `ddl-auto: validate` with `ddl-auto: none` and remove all `org.liquibase` log entries; add Flyway config. Change these sections:

```yaml
# In spring: block, replace the jpa: section:
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: none     # HAPI's internal schema migrator + Flyway own RBAC tables
    show-sql: false

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
```

In the `logging.level` section, replace `org.liquibase: WARN` with `org.flywaydb: WARN`.

- [ ] **Step 5: Delete the Liquibase changelog directory**

```bash
rm -rf src/main/resources/db/changelog
```

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V1__create_rbac_tables.sql \
        src/main/resources/application.yml \
        src/main/resources/application-dev.yml \
        src/main/resources/application-prod.yml
git rm -r src/main/resources/db/changelog
git commit -m "feat: replace liquibase with flyway, convert RBAC schema to V1 SQL migration"
```

---

## Task 3: Configure the JPA Server (Spring Beans)

**Files:**
- Modify: `src/main/java/it/arsinfo/fhir/FhirServerApplication.java`
- Modify: `src/main/java/it/arsinfo/fhir/config/FhirServerConfig.java`

- [ ] **Step 1: Add `@EntityScan` to `FhirServerApplication`**

Replace the entire file:

```java
package it.arsinfo.fhir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@EntityScan({
    "ca.uhn.fhir.jpa.model.entity",  // HAPI JPA resource tables
    "ca.uhn.fhir.jpa.entity",         // HAPI JPA term/subscription entities
    "it.arsinfo.fhir.domain.entity"   // RBAC entities
})
public class FhirServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FhirServerApplication.class, args);
    }
}
```

- [ ] **Step 2: Add HAPI JPA config import and `JpaStorageSettings` bean to `FhirServerConfig`**

Replace `src/main/java/it/arsinfo/fhir/config/FhirServerConfig.java`:

```java
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
     * Full-text search disabled (SQL-only). Reasonable page limits set.
     */
    @Bean
    public JpaStorageSettings jpaStorageSettings() {
        JpaStorageSettings settings = new JpaStorageSettings();
        settings.setFulltextSearchEnabled(false);
        settings.setAllowMultipleDelete(true);
        settings.setDefaultPageSize(20);
        settings.setMaximumPageSize(200);
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
```

> **Note on `@Import(JpaR4Config.class)`:** This imports HAPI's R4 Spring configuration, which registers all R4 resource DAOs as `IResourceProvider` beans and wires the system DAO. If you get `ClassNotFoundException` for `JpaR4Config`, verify the package by running:
> ```bash
> jar tf ~/.m2/repository/ca/uhn/hapi/fhir/hapi-fhir-jpaserver-base/8.0.0/hapi-fhir-jpaserver-base-8.0.0.jar \
>   | grep -i "JpaR4Config"
> ```
> Use the exact package path found in the jar.

- [ ] **Step 3: Verify compilation**

```bash
mvn compile -DskipTests 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`. Common errors at this stage:
- `ClassNotFoundException: JpaR4Config` → check Step 2 note above for exact package
- `No qualifying bean of type 'EntityManagerFactory'` → `@EntityScan` is not being picked up; verify it's on `FhirServerApplication`
- `BeanDefinitionOverrideException` → HAPI config is trying to define a bean already defined; add `spring.main.allow-bean-definition-overriding=true` to `application.yml` as a temporary measure and investigate which bean conflicts

- [ ] **Step 4: Commit**

```bash
git add src/main/java/it/arsinfo/fhir/FhirServerApplication.java \
        src/main/java/it/arsinfo/fhir/config/FhirServerConfig.java
git commit -m "feat: wire hapi-fhir-jpaserver-base with JpaR4Config and JpaStorageSettings"
```

---

## Task 4: Rewrite `FhirRestfulServlet` for JPA Mode

**Files:**
- Modify: `src/main/java/it/arsinfo/fhir/servlet/FhirRestfulServlet.java`

The existing bean-discovery approach (`getBeansOfType(IResourceProvider.class)`) already works for JPA: `JpaR4Config` registers one `IResourceProvider` per R4 resource type. The only addition needed is the system provider (for `$everything`, `_history`, etc.).

- [ ] **Step 1: Add system provider registration to `initialize()`**

Replace `src/main/java/it/arsinfo/fhir/servlet/FhirRestfulServlet.java`:

```java
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

public class FhirRestfulServlet extends RestfulServer {

    private final SmartAuthorizationInterceptor authorizationInterceptor;
    private final ApplicationContext applicationContext;
    private final Environment environment;

    public FhirRestfulServlet(SmartAuthorizationInterceptor authorizationInterceptor,
                              ApplicationContext applicationContext,
                              Environment environment) {
        super(FhirContext.forR4());
        this.authorizationInterceptor = authorizationInterceptor;
        this.applicationContext       = applicationContext;
        this.environment              = environment;
    }

    @Override
    protected void initialize() throws ServletException {
        setDefaultPrettyPrint(true);
        setDefaultResponseEncoding(ca.uhn.fhir.rest.api.EncodingEnum.JSON);

        // JPA DAOs are registered as IResourceProvider beans by JpaR4Config
        registerProviders(applicationContext.getBeansOfType(IResourceProvider.class).values());

        // System provider handles $everything, _history, $validate, batch, transaction
        registerProvider(applicationContext.getBean(JpaSystemProvider.class));

        LoggingInterceptor loggingInterceptor = new LoggingInterceptor();
        loggingInterceptor.setLoggerName("fhir.access");
        registerInterceptor(loggingInterceptor);

        registerInterceptor(authorizationInterceptor);

        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        if (isDev) {
            registerInterceptor(new ResponseHighlighterInterceptor());
        }
    }
}
```

> **Note on `JpaSystemProvider`:** In HAPI 8.x this class is in `ca.uhn.fhir.jpa.provider`. If `applicationContext.getBean(JpaSystemProvider.class)` throws `NoSuchBeanDefinitionException`, check whether HAPI 8.x uses a different class name (e.g. `JpaSystemProviderR4`) with:
> ```bash
> jar tf ~/.m2/repository/ca/uhn/hapi/fhir/hapi-fhir-jpaserver-base/8.0.0/hapi-fhir-jpaserver-base-8.0.0.jar \
>   | grep "provider/JpaSystem"
> ```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -DskipTests 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/it/arsinfo/fhir/servlet/FhirRestfulServlet.java
git commit -m "feat: update FhirRestfulServlet to register JPA system provider"
```

---

## Task 5: Remove In-Memory Providers, Add Dev Data Seeder

**Files:**
- Delete: `src/main/java/it/arsinfo/fhir/provider/InMemoryPatientProvider.java`
- Delete: `src/main/java/it/arsinfo/fhir/provider/InMemoryObservationProvider.java`
- Create: `src/main/java/it/arsinfo/fhir/init/FhirDataSeeder.java`

- [ ] **Step 1: Delete the in-memory providers**

```bash
git rm src/main/java/it/arsinfo/fhir/provider/InMemoryPatientProvider.java \
       src/main/java/it/arsinfo/fhir/provider/InMemoryObservationProvider.java
```

- [ ] **Step 2: Create `FhirDataSeeder`**

Create `src/main/java/it/arsinfo/fhir/init/FhirDataSeeder.java`:

```java
package it.arsinfo.fhir.init;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Profile("dev")
public class FhirDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FhirDataSeeder.class);

    private final IFhirResourceDao<Patient>     patientDao;
    private final IFhirResourceDao<Observation> observationDao;

    public FhirDataSeeder(IFhirResourceDao<Patient> patientDao,
                          IFhirResourceDao<Observation> observationDao) {
        this.patientDao     = patientDao;
        this.observationDao = observationDao;
    }

    @Override
    public void run(ApplicationArguments args) {
        // ddl-auto=create-drop guarantees empty tables on every dev startup — no idempotency guard needed
        IdType janeDoeId = seedPatient("Jane", "Doe", Enumerations.AdministrativeGender.FEMALE);
        IdType robertSmithId = seedPatient("Robert", "Smith", Enumerations.AdministrativeGender.MALE);

        seedObservation(janeDoeId,    "8867-4", "Heart rate",        72, "beats/min");
        seedObservation(janeDoeId,    "8310-5", "Body temperature",  37, "Cel");
        seedObservation(robertSmithId,"8867-4", "Heart rate",        80, "beats/min");

        log.info("Seeded 2 dev Patients and 3 dev Observations");
    }

    private IdType seedPatient(String given, String family,
                                Enumerations.AdministrativeGender gender) {
        Patient p = new Patient();
        p.addName().setFamily(family).addGiven(given);
        p.setGender(gender);
        return (IdType) patientDao.create(p).getId();
    }

    private void seedObservation(IdType patientId, String loincCode,
                                  String display, int value, String unit) {
        Observation o = new Observation();
        o.setStatus(Observation.ObservationStatus.FINAL);
        o.setSubject(new Reference("Patient/" + patientId.getIdPart()));
        o.getCode().addCoding()
                .setSystem("http://loinc.org")
                .setCode(loincCode)
                .setDisplay(display);
        Quantity q = new Quantity();
        q.setValue(new BigDecimal(value)).setUnit(unit);
        o.setValue(q);
        observationDao.create(o);
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
mvn compile -DskipTests 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Verify the app starts in dev mode**

```bash
mvn spring-boot:run &
sleep 15
curl -s http://localhost:8080/fhir/metadata | python3 -m json.tool | head -20
kill %1
```

Expected: `fhirVersion: "4.0.1"` in the capability statement JSON. If the server fails to start, check logs for bean wiring errors.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/it/arsinfo/fhir/init/FhirDataSeeder.java
git commit -m "feat: add dev FhirDataSeeder, remove in-memory providers"
```

---

## Task 6: Extend `SmartRuleBuilder` for JPA Operations (TDD)

**Files:**
- Modify: `src/test/java/it/arsinfo/fhir/interceptor/SmartRuleBuilderTest.java`
- Modify: `src/main/java/it/arsinfo/fhir/interceptor/SmartRuleBuilder.java`

### Step 6a — `$validate` rule (always allow for authenticated users)

- [ ] **Step 1: Write the failing test**

Add to `SmartRuleBuilderTest`:

```java
@Test
void buildRules_validateOperationAllowed_regardlessOfScopes() {
    // $validate does not read or write data — always allow for any authenticated user
    List<SmartScope> scopes = parser.parse("user/Patient.read");
    List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.empty());

    boolean hasValidateRule = rules.stream()
            .anyMatch(r -> r.getName() != null && r.getName().contains("validate"));
    assertThat(hasValidateRule).isTrue();
}
```

- [ ] **Step 2: Run to confirm it fails**

```bash
mvn test -Dtest=SmartRuleBuilderTest#buildRules_validateOperationAllowed_regardlessOfScopes 2>&1 | tail -15
```

Expected: `FAILED` — no validate rule exists yet.

- [ ] **Step 3: Implement `addValidateRules()` in `SmartRuleBuilder`**

In `buildRules()`, add this call just before the terminal `denyAll()` line:

```java
rules.addAll(addValidateRules(ruleIndex));
```

Add the private method:

```java
private List<IAuthRule> addValidateRules(AtomicInteger idx) {
    return new RuleBuilder()
            .allow("r-" + idx.getAndIncrement() + "-validate")
            .operation()
            .named("$validate")
            .atAnyLevel()
            .andAllowAllResponses()
            .build();
}
```

- [ ] **Step 4: Run to confirm it passes**

```bash
mvn test -Dtest=SmartRuleBuilderTest#buildRules_validateOperationAllowed_regardlessOfScopes 2>&1 | tail -10
```

Expected: `PASSED`

### Step 6b — `$everything` rule (requires read scope on Patient)

- [ ] **Step 5: Write the failing test**

Add to `SmartRuleBuilderTest`:

```java
@Test
void buildRules_everythingAllowed_whenUserHasPatientRead() {
    List<SmartScope> scopes = parser.parse("user/Patient.read");
    List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.empty());

    boolean hasEverythingRule = rules.stream()
            .anyMatch(r -> r.getName() != null && r.getName().contains("everything"));
    assertThat(hasEverythingRule).isTrue();
}

@Test
void buildRules_everythingAllowed_forPatientContextWithMatchingId() {
    List<SmartScope> scopes = parser.parse("patient/Patient.read");
    List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.of("Patient/p1"));

    boolean hasEverythingRule = rules.stream()
            .anyMatch(r -> r.getName() != null && r.getName().contains("everything"));
    assertThat(hasEverythingRule).isTrue();
}
```

- [ ] **Step 6: Run to confirm both fail**

```bash
mvn test -Dtest="SmartRuleBuilderTest#buildRules_everythingAllowed_whenUserHasPatientRead+buildRules_everythingAllowed_forPatientContextWithMatchingId" 2>&1 | tail -15
```

Expected: both `FAILED`.

- [ ] **Step 7: Implement `addEverythingRules()` in `SmartRuleBuilder`**

In `buildRules()`, add this call after `addValidateRules` and before `denyAll()`:

```java
rules.addAll(addEverythingRules(effectiveScopes, patientId, ruleIndex));
```

Add the private method:

```java
private List<IAuthRule> addEverythingRules(List<SmartScope> scopes,
                                            Optional<String> patientId,
                                            AtomicInteger idx) {
    List<IAuthRule> rules = new ArrayList<>();
    for (SmartScope scope : scopes) {
        if (!scope.allows(SmartPermission.READ) && !scope.allows(SmartPermission.SEARCH)) {
            continue;
        }
        boolean appliesToPatient = scope.isWildcard() || "Patient".equals(scope.resourceType());
        if (!appliesToPatient) continue;

        String prefix = "r-" + idx.getAndIncrement() + "-everything";
        if (scope.context() == SmartContext.PATIENT && patientId.isPresent()) {
            IIdType ref = new IdType("Patient", extractPatientIdValue(patientId.get()));
            rules.addAll(new RuleBuilder()
                    .allow(prefix + "-patient")
                    .operation().named("$everything")
                    .onInstance(ref)
                    .andAllowAllResponses()
                    .build());
        } else if (scope.context() == SmartContext.USER || scope.context() == SmartContext.SYSTEM) {
            rules.addAll(new RuleBuilder()
                    .allow(prefix + "-user")
                    .operation().named("$everything")
                    .onAnyInstance()
                    .andAllowAllResponses()
                    .build());
        }
    }
    return rules;
}
```

- [ ] **Step 8: Run to confirm both pass**

```bash
mvn test -Dtest="SmartRuleBuilderTest#buildRules_everythingAllowed_whenUserHasPatientRead+buildRules_everythingAllowed_forPatientContextWithMatchingId" 2>&1 | tail -10
```

Expected: both `PASSED`.

### Step 6c — `_history` system-level rule (requires `system/*.read`)

- [ ] **Step 9: Write the failing test**

Add to `SmartRuleBuilderTest`:

```java
@Test
void buildRules_systemHistoryAllowed_forSystemReadScope() {
    List<SmartScope> scopes = parser.parse("system/*.read");
    List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.empty());

    boolean hasHistoryRule = rules.stream()
            .anyMatch(r -> r.getName() != null && r.getName().contains("history"));
    assertThat(hasHistoryRule).isTrue();
}

@Test
void buildRules_systemHistoryNotAllowed_forUserReadScope() {
    // user/*.read gives per-resource read but not system-level _history
    List<SmartScope> scopes = parser.parse("user/*.read");
    List<IAuthRule> rules = ruleBuilder.buildRules(scopes, Optional.empty());

    boolean hasHistoryRule = rules.stream()
            .anyMatch(r -> r.getName() != null && r.getName().contains("history"));
    assertThat(hasHistoryRule).isFalse();
}
```

- [ ] **Step 10: Run to confirm both fail**

```bash
mvn test -Dtest="SmartRuleBuilderTest#buildRules_systemHistoryAllowed_forSystemReadScope+buildRules_systemHistoryNotAllowed_forUserReadScope" 2>&1 | tail -15
```

Expected: both `FAILED`.

- [ ] **Step 11: Implement `addSystemHistoryRules()` in `SmartRuleBuilder`**

In `buildRules()`, add this call after `addEverythingRules` and before `denyAll()`:

```java
rules.addAll(addSystemHistoryRules(effectiveScopes, ruleIndex));
```

Add the private method:

```java
private List<IAuthRule> addSystemHistoryRules(List<SmartScope> scopes, AtomicInteger idx) {
    boolean hasSystemReadAll = scopes.stream()
            .anyMatch(s -> s.context() == SmartContext.SYSTEM
                    && s.isWildcard()
                    && (s.allows(SmartPermission.READ) || s.allows(SmartPermission.SEARCH)));
    if (!hasSystemReadAll) return List.of();

    return new RuleBuilder()
            .allow("r-" + idx.getAndIncrement() + "-system-history")
            .operation()
            .named("_history")
            .atAnyLevel()
            .andAllowAllResponses()
            .build();
}
```

- [ ] **Step 12: Run all `SmartRuleBuilderTest` tests**

```bash
mvn test -Dtest=SmartRuleBuilderTest 2>&1 | tail -20
```

Expected: all tests `PASSED`.

- [ ] **Step 13: Commit**

```bash
git add src/main/java/it/arsinfo/fhir/interceptor/SmartRuleBuilder.java \
        src/test/java/it/arsinfo/fhir/interceptor/SmartRuleBuilderTest.java
git commit -m "feat: extend SmartRuleBuilder with \$validate, \$everything, and system _history rules"
```

---

## Task 7: Write End-to-End Integration Test (`FhirJpaIT`)

**Files:**
- Create: `src/test/java/it/arsinfo/fhir/FhirJpaIT.java`

> **Important:** The HAPI FHIR servlet is registered via `ServletRegistrationBean` — it is NOT Spring MVC's `DispatcherServlet`. `MockMvc` only dispatches to `DispatcherServlet` and will not route `/fhir/**` requests. Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` instead.
>
> JWT auth: `SmartAuthorizationInterceptor.extractJwt()` checks for `JwtAuthenticationToken`. A `@MockBean JwtDecoder` intercepts Spring Security's `BearerTokenAuthenticationFilter` — when the test sends `Authorization: Bearer <token>`, Spring Security calls `jwtDecoder.decode(token)`, gets back a pre-built `Jwt`, and puts a `JwtAuthenticationToken` in the security context. The interceptor then reads the JWT claims normally.

- [ ] **Step 1: Create `FhirJpaIT`**

Create `src/test/java/it/arsinfo/fhir/FhirJpaIT.java`:

```java
package it.arsinfo.fhir;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class FhirJpaIT {

    @LocalServerPort int port;

    @Autowired TestRestTemplate rest;

    @MockBean JwtDecoder jwtDecoder;

    private static final String FHIR_JSON = "application/fhir+json";
    private static final String TOKEN_ALL  = "token-all";   // scope: user/*.cruds
    private static final String TOKEN_READ = "token-read";  // scope: user/*.read
    private static final String TOKEN_SYS  = "token-sys";   // scope: system/*.read

    @BeforeEach
    void setupJwt() {
        when(jwtDecoder.decode(TOKEN_ALL)).thenReturn(jwt("user/*.cruds"));
        when(jwtDecoder.decode(TOKEN_READ)).thenReturn(jwt("user/*.read"));
        when(jwtDecoder.decode(TOKEN_SYS)).thenReturn(jwt("system/*.read"));
    }

    private Jwt jwt(String scope) {
        return Jwt.withTokenValue("tok")
                .header("alg", "none")
                .claim("scope", scope)
                .claim("sub", "test-user")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private String base() { return "http://localhost:" + port; }

    private HttpHeaders bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + token);
        h.setContentType(MediaType.parseMediaType(FHIR_JSON));
        h.set("Accept", FHIR_JSON);
        return h;
    }

    // ── Capability statement ─────────────────────────────────────────────────

    @Test
    void metadata_isPublic_noAuth() {
        ResponseEntity<String> resp = rest.getForEntity(base() + "/fhir/metadata", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"fhirVersion\"");
    }

    // ── Patient CRUD ─────────────────────────────────────────────────────────

    @Test
    void createAndReadPatient_roundTrip() {
        String body = """
            {"resourceType":"Patient","name":[{"family":"Testmann","given":["Hans"]}],"gender":"male"}
            """;
        ResponseEntity<String> created = rest.exchange(
                base() + "/fhir/Patient", HttpMethod.POST,
                new HttpEntity<>(body, bearer(TOKEN_ALL)), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String location = created.getHeaders().getFirst("Location");
        assertThat(location).isNotNull();
        String id = location.replaceAll(".*/Patient/(\\w+)/.*", "$1");

        ResponseEntity<String> read = rest.exchange(
                base() + "/fhir/Patient/" + id, HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_READ)), String.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody()).contains("Testmann");
    }

    @Test
    void readPatient_noAuth_returns401or403() {
        ResponseEntity<String> resp = rest.getForEntity(base() + "/fhir/Patient/1", String.class);
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    // ── Search ───────────────────────────────────────────────────────────────

    @Test
    void searchPatient_byFamily_returnsBundle() {
        String body = """
            {"resourceType":"Patient","name":[{"family":"Searchable99","given":["Test"]}]}
            """;
        rest.exchange(base() + "/fhir/Patient", HttpMethod.POST,
                new HttpEntity<>(body, bearer(TOKEN_ALL)), String.class);

        ResponseEntity<String> search = rest.exchange(
                base() + "/fhir/Patient?family=Searchable99", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_READ)), String.class);
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(search.getBody()).contains("\"Bundle\"");
    }

    // ── $validate ────────────────────────────────────────────────────────────

    @Test
    void validateOperation_allowedWithAnyAuthenticatedScope() {
        String body = """
            {"resourceType":"Patient","name":[{"family":"ValidateMe"}]}
            """;
        ResponseEntity<String> resp = rest.exchange(
                base() + "/fhir/Patient/$validate", HttpMethod.POST,
                new HttpEntity<>(body, bearer(TOKEN_READ)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("OperationOutcome");
    }

    // ── $everything ──────────────────────────────────────────────────────────

    @Test
    void everythingOperation_allowedWithUserPatientRead() {
        // Create a patient
        String body = """
            {"resourceType":"Patient","name":[{"family":"EverythingTest99"}]}
            """;
        ResponseEntity<String> created = rest.exchange(
                base() + "/fhir/Patient", HttpMethod.POST,
                new HttpEntity<>(body, bearer(TOKEN_ALL)), String.class);
        String id = created.getHeaders().getFirst("Location")
                .replaceAll(".*/Patient/(\\w+)/.*", "$1");

        ResponseEntity<String> resp = rest.exchange(
                base() + "/fhir/Patient/" + id + "/$everything", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_READ)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"Bundle\"");
    }

    @Test
    void everythingOperation_deniedWithObservationOnlyScope() {
        when(jwtDecoder.decode("token-obs")).thenReturn(jwt("user/Observation.read"));
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer token-obs");
        h.set("Accept", FHIR_JSON);

        ResponseEntity<String> resp = rest.exchange(
                base() + "/fhir/Patient/1/$everything", HttpMethod.GET,
                new HttpEntity<>(h), String.class);
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    // ── _history ─────────────────────────────────────────────────────────────

    @Test
    void resourceHistory_allowedWithReadScope() {
        String body = """
            {"resourceType":"Patient","name":[{"family":"HistoryTest99"}]}
            """;
        ResponseEntity<String> created = rest.exchange(
                base() + "/fhir/Patient", HttpMethod.POST,
                new HttpEntity<>(body, bearer(TOKEN_ALL)), String.class);
        String id = created.getHeaders().getFirst("Location")
                .replaceAll(".*/Patient/(\\w+)/.*", "$1");

        ResponseEntity<String> resp = rest.exchange(
                base() + "/fhir/Patient/" + id + "/_history", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_READ)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"Bundle\"");
    }

    @Test
    void systemHistory_allowedWithSystemScope() {
        ResponseEntity<String> resp = rest.exchange(
                base() + "/fhir/_history", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_SYS)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"Bundle\"");
    }

    @Test
    void systemHistory_deniedWithUserScope() {
        ResponseEntity<String> resp = rest.exchange(
                base() + "/fhir/_history", HttpMethod.GET,
                new HttpEntity<>(bearer(TOKEN_READ)), String.class);
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }
}
```

- [ ] **Step 2: Run the integration test**

```bash
mvn test -Dtest=FhirJpaIT 2>&1 | tail -40
```

Expected: all tests `PASSED`. Common failures:
- `401` instead of `403` on auth-denied tests → acceptable, update assertions to `.isIn(401, 403)`
- `NoSuchBeanDefinitionException: JpaSystemProvider` → see Task 4 note on checking the exact class name in the jar
- `$validate` returns `400` instead of `200` → HAPI may require wrapping the resource in a `Parameters` resource; check HAPI docs for `$validate` request format

- [ ] **Step 3: Commit**

```bash
git add src/test/java/it/arsinfo/fhir/FhirJpaIT.java
git commit -m "test: add FhirJpaIT end-to-end integration test for JPA server"
```

---

## Task 7b: `FhirDataSeederTest`

**Files:**
- Create: `src/test/java/it/arsinfo/fhir/init/FhirDataSeederTest.java`

This test verifies the dev seeder produces the expected Patient and Observation data by checking the DAO state after the Spring context starts (the seeder runs automatically as an `ApplicationRunner`).

- [ ] **Step 1: Create `FhirDataSeederTest`**

Create `src/test/java/it/arsinfo/fhir/init/FhirDataSeederTest.java`:

```java
package it.arsinfo.fhir.init;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class FhirDataSeederTest {

    @MockBean JwtDecoder jwtDecoder;  // prevent Keycloak OIDC discovery

    @Autowired IFhirResourceDao<Patient>     patientDao;
    @Autowired IFhirResourceDao<Observation> observationDao;

    @Test
    void seeder_createsTwoPatients() {
        int count = patientDao.search(new SearchParameterMap()).size();
        assertThat(count).isEqualTo(2);
    }

    @Test
    void seeder_createsThreeObservations() {
        int count = observationDao.search(new SearchParameterMap()).size();
        assertThat(count).isEqualTo(3);
    }

    @Test
    void seeder_patientFamilyNamesAreCorrect() {
        var results = patientDao.search(new SearchParameterMap()).getResources(0, 10);
        var families = results.stream()
                .map(r -> ((Patient) r).getNameFirstRep().getFamily())
                .toList();
        assertThat(families).containsExactlyInAnyOrder("Doe", "Smith");
    }
}
```

- [ ] **Step 2: Run the test**

```bash
mvn test -Dtest=FhirDataSeederTest 2>&1 | tail -15
```

Expected: all 3 tests `PASSED`.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/it/arsinfo/fhir/init/FhirDataSeederTest.java
git commit -m "test: add FhirDataSeederTest to verify dev data seeding"
```

---

## Task 8: Final Verification (all tests green)

- [ ] **Step 1: Run full test suite**

```bash
mvn clean verify 2>&1 | tail -30
```

Expected: `BUILD SUCCESS` with all tests passing.

- [ ] **Step 2: Spot-check the running server**

```bash
mvn spring-boot:run &
sleep 20

# Capability statement
curl -s http://localhost:8080/fhir/metadata | python3 -c "import sys,json; d=json.load(sys.stdin); print('FHIR:', d['fhirVersion'], '| Resources:', len(d.get('rest',[{}])[0].get('resource',[])))"

# H2 console reachable
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/h2-console

kill %1
```

Expected output (approximate):
```
FHIR: 4.0.1 | Resources: 146
200
```

The resource count of ~146 confirms all R4 types are registered via JPA DAOs (vs. 2 with the old in-memory server).

- [ ] **Step 3: Final commit if any cleanup needed**

```bash
git status
# If clean: nothing to do.
# If there are stray files, commit them with an appropriate message.
```
