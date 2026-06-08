# Design: Migrate to Full HAPI FHIR JPA Server

**Date:** 2026-06-08
**Approach:** Big Bang (single PR)
**Status:** Approved

---

## Goal

Replace the current HAPI plain server (in-memory `ConcurrentHashMap` providers) with `hapi-fhir-jpaserver-base` so all FHIR R4 resources are persisted in PostgreSQL and survive restarts. The existing SMART on FHIR RBAC auth layer is preserved and extended to cover JPA-specific operations. Liquibase is replaced by Flyway as the single schema migration tool.

---

## Architecture

### Before

```
Spring Boot App
├── HAPI Plain Server  (FhirRestfulServlet extends RestfulServer)
│   ├── InMemoryPatientProvider  (ConcurrentHashMap)
│   ├── InMemoryObservationProvider  (ConcurrentHashMap)
│   └── SmartAuthorizationInterceptor
├── Spring Data JPA  (Role, RoleScope, UserRoleAssignment only)
│   └── Schema: Liquibase XML
└── Spring Security  (JWT resource server + OAuth2 client)
```

### After

```
Spring Boot App
├── HAPI JPA Server  (FhirRestfulServlet, JPA-wired)
│   ├── HAPI JPA DAOs  (all R4 resources, auto-registered via DaoRegistry)
│   ├── HAPI system providers  ($everything, _history, $validate, etc.)
│   └── SmartAuthorizationInterceptor  (extended for JPA ops)
├── Spring Data JPA  (shared EntityManagerFactory)
│   ├── HAPI JPA entities  (ca.uhn.fhir.jpa.model.entity.*)
│   ├── RBAC entities  (it.arsinfo.fhir.domain.entity.*)
│   └── Schema: Flyway  (RBAC tables) + HAPI internal migrator (HAPI tables)
└── Spring Security  (unchanged)
```

The two in-memory providers are deleted. All FHIR resource persistence moves to HAPI DAOs backed by PostgreSQL. Spring Security, the RBAC domain, and the Thymeleaf admin UI are untouched except for wiring.

---

## Dependencies (pom.xml)

### Remove

| Artifact | Reason |
|---|---|
| `hapi-fhir-server` | Replaced by `hapi-fhir-jpaserver-base` |
| `hapi-fhir-structures-r4` | Pulled in transitively |
| `hapi-fhir-validation-resources-r4` | Pulled in transitively |
| `liquibase-core` | Replaced by Flyway |

### Add

| Artifact | Reason |
|---|---|
| `hapi-fhir-jpaserver-base:8.0.0` | Core JPA server: DAOs, DaoRegistry, JpaStorageSettings, all R4 entity mappings |
| `flyway-core` | Schema migration runner |
| `flyway-database-postgresql` | PostgreSQL dialect (required since Flyway 10) |

`hapi-fhir-jpaserver-base` pulls in Hibernate ORM and Hibernate Search transitively. Hibernate Search / Lucene is explicitly disabled via `JpaStorageSettings.setFulltextSearchEnabled(false)`.

---

## JPA Server Wiring

### `JpaStorageSettings` (new bean in `FhirServerConfig`)

- `setFulltextSearchEnabled(false)` — SQL-only search, no Lucene
- `setAllowMultipleDelete(true)` — needed for `DELETE ?family=Doe` style operations
- `setDefaultPageSize(20)`, `setMaximumPageSize(200)`
- Resource types: all R4 (default, no restriction)

### `LocalContainerEntityManagerFactoryBean` (replaces Spring Boot auto-config)

Scans two package trees in one `EntityManagerFactory`:
- `ca.uhn.fhir.jpa.model.entity` — HAPI's JPA entities
- `it.arsinfo.fhir.domain.entity` — RBAC entities

Spring Boot JPA auto-configuration excluded via:
```java
@SpringBootApplication(exclude = HibernateJpaAutoConfiguration.class)
```

One shared datasource and one `JpaTransactionManager`.

### `FhirRestfulServlet` (replaces plain `RestfulServer`)

New `initialize()`:
1. Calls HAPI JPA bootstrap to auto-register all R4 resource providers from `DaoRegistry`
2. Registers HAPI plain system providers (for `$everything`, `_history`, `$validate`)
3. Registers `SmartAuthorizationInterceptor` (wire-up unchanged)
4. Registers `LoggingInterceptor` + dev-only `ResponseHighlighterInterceptor` (unchanged)

The existing `IResourceProvider` bean-scanning loop is removed.

### Deleted files

- `src/main/java/it/arsinfo/fhir/provider/InMemoryPatientProvider.java`
- `src/main/java/it/arsinfo/fhir/provider/InMemoryObservationProvider.java`

### New: `FhirDataSeeder` (dev profile only)

A `CommandLineRunner` bean annotated `@Profile("dev")` seeds the same two Patient and three Observation test fixtures via HAPI DAOs on startup. Mirrors the pattern of `BuiltInRoleSeeder`.

---

## Database / Flyway Migration

### Schema management per profile

| Profile | HAPI schema | RBAC schema |
|---|---|---|
| `dev` (H2) | `ddl-auto=create-drop` — Hibernate creates on startup; HAPI internal migrator disabled | Same ephemeral H2, no migrations needed |
| `prod` (PostgreSQL) | HAPI's internal schema migrator runs versioned scripts at startup | Flyway runs our RBAC migrations |

HAPI 8.x ships its own internal schema migration runner (`ca.uhn.fhir.jpa.migrate`) that manages its ~150 JPA tables independently. HAPI's migrations are **not** absorbed into Flyway — doing so would couple the project to HAPI internals. Both tools write to the same PostgreSQL database with no table overlap.

### Flyway migration files

```
src/main/resources/db/migration/
└── V1__create_rbac_tables.sql    ← converted from 001-create-role-tables.xml
```

Content: `CREATE TABLE role`, `CREATE TABLE role_scope`, `CREATE TABLE user_role_assignment` with identical column definitions and constraints as the existing Liquibase changeset.

### Liquibase removal

- Remove `liquibase-core` from `pom.xml`
- Delete `src/main/resources/db/changelog/` directory
- Remove `spring.liquibase.*` from `application.yml`
- Add to `application-prod.yml`: `spring.flyway.locations=classpath:db/migration`, `spring.flyway.enabled=true`
- Add to `application-dev.yml`: `spring.flyway.enabled=false`

---

## Auth Layer Extension

Only `SmartRuleBuilder` changes. All other auth classes (`SmartAuthorizationInterceptor`, `RbacScopeResolver`, `SmartScopeParser`, `JwtClaimsExtractor`) are untouched.

### New operations and their scope rules

| Operation | Rule |
|---|---|
| `GET /fhir/Patient/123/$everything` | Requires `patient/Patient.read` or `patient/*.read` (patient-context) / `user/Patient.read` or `user/*.read` (user-context) |
| `GET /fhir/Patient/123/_history` | Treated as read — same scope as `GET /fhir/Patient/123` |
| `GET /fhir/_history` | System-level history — requires `system/*.read` |
| `POST /fhir/Patient/$validate` | Allowed for any authenticated principal (no data accessed) |
| `POST /fhir/` batch/transaction | HAPI decomposes bundle entries and re-runs the rule chain per entry automatically — no code change needed |
| `GET /fhir/metadata` | Already public — no change |

### Implementation in `SmartRuleBuilder`

Three new private methods added to `buildRules(SmartContext)` before the terminal `denyAll()`:

1. **`addHistoryRules()`** — `RuleBuilder.allow().operation().named("_history")` scoped to the same resource type/compartment the user has read access to.
2. **`addEverythingRules()`** — `RuleBuilder.allow().operation().named("$everything")` on Patient, guarded by existing patient-compartment check.
3. **`addValidateRules()`** — unconditionally allows `$validate` for any authenticated principal.

---

## Testing Strategy

### Existing tests — unchanged

| Test | Status |
|---|---|
| `SmartScopeParserTest` | Unchanged — pure unit test |
| `JwtClaimsExtractorTest` | Unchanged |
| `RoleRestControllerIT` | Unchanged — RBAC REST API only |
| `AdminUsersControllerIT` | Unchanged — Thymeleaf UI only |

### Modified tests

| Test | Change |
|---|---|
| `SmartRuleBuilderTest` | Add cases for `$everything`, `_history`, `$validate` rules |

### New tests

**`FhirJpaIT`** (`@SpringBootTest`, `dev` profile, H2)
- `POST /fhir/Patient` → 201, resource persisted and retrievable
- `GET /fhir/Patient/{id}` → 200 with correct scope, 403 without
- `GET /fhir/Patient?family=Doe` → 200, search returns correct results
- `GET /fhir/Patient/{id}/$everything` → 200 with patient scope, 403 without
- `GET /fhir/Patient/{id}/_history` → 200 with read scope, 403 without
- `POST /fhir/Patient/$validate` → 200 for any authenticated user
- `POST /fhir/` batch → 200, per-resource auth enforced on each entry

**`FhirDataSeederTest`** (unit)
- Confirms dev seeder produces expected Patient and Observation fixtures via DAO layer

### Test infrastructure

All integration tests continue to use `@WithMockUser` + `@MockBean` for `JwtDecoder`. H2 + `ddl-auto=create-drop` gives a clean schema per test run. No change to test auth setup.

---

## Migration Checklist (for implementation)

- [ ] Update `pom.xml`: remove old HAPI artifacts + Liquibase; add jpaserver-base + Flyway
- [ ] Exclude Spring Boot JPA auto-config in `FhirServerApplication`
- [ ] Add `JpaStorageSettings` bean to `FhirServerConfig`
- [ ] Wire `LocalContainerEntityManagerFactoryBean` scanning both entity packages
- [ ] Rewrite `FhirRestfulServlet.initialize()` for JPA server
- [ ] Delete `InMemoryPatientProvider` and `InMemoryObservationProvider`
- [ ] Add `FhirDataSeeder` (dev profile only)
- [ ] Create `src/main/resources/db/migration/V1__create_rbac_tables.sql`
- [ ] Delete `src/main/resources/db/changelog/`
- [ ] Update `application.yml`, `application-dev.yml`, `application-prod.yml` for Flyway
- [ ] Add `addHistoryRules()`, `addEverythingRules()`, `addValidateRules()` to `SmartRuleBuilder`
- [ ] Add new test cases to `SmartRuleBuilderTest`
- [ ] Write `FhirJpaIT` integration test
- [ ] Write `FhirDataSeederTest` unit test
- [ ] `mvn clean verify` passes
