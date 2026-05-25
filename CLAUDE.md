# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Full build with tests
mvn clean install

# Build without tests
mvn clean package -DskipTests

# Run dev server (H2 in-memory, auto-seeded roles)
mvn spring-boot:run

# Run with PostgreSQL (requires docker-compose services)
mvn spring-boot:run -Pprod
```

## Tests

```bash
# All tests
mvn test

# Single test class
mvn test -Dtest=SmartScopeParserTest

# Integration tests only
mvn test -Dtest=*IT

# Full verify
mvn verify
```

Tests run against an H2 in-memory DB with the `dev` Spring profile. Integration tests use `@SpringBootTest` + `@AutoConfigureMockMvc`; auth is mocked via `@WithMockUser`.

## Local Infrastructure

```bash
docker-compose up -d   # Starts Keycloak (port 8180) + PostgreSQL (port 5432)
```

Key ports: `8080` app, `8180` Keycloak, `5432` PostgreSQL. The H2 console is at `/h2-console` (dev only).

## Architecture

**Purpose:** HAPI FHIR REST Server (v8.0.0, R4) with a SMART on FHIR RBAC layer. Supports any OIDC provider (Keycloak, Auth0, Okta, Azure AD, AWS Cognito) via configurable JWT claims.

**Request flow:**

```
HTTP Request
  ↓
Spring Security (JWT via JWKS URI)
  ↓
SmartAuthorizationInterceptor  ← HAPI interceptor hook
  ├─ RbacScopeResolver
  │  ├─ JWT scope claim      → SmartScopeParser
  │  ├─ JWT roles claim      → DB role scopes → SmartScopeParser
  │  └─ DB user-role assignments → DB role scopes → SmartScopeParser
  └─ SmartRuleBuilder
     └─ List<IAuthRule> → HAPI AuthorizationInterceptor.authorize()
```

**Two Spring Security filter chains:**
1. `/fhir/**` and `/api/admin/**` — stateless JWT bearer token validation
2. `/admin/**` — session-aware (Thymeleaf + CSRF) with JWT bearer as fallback

**Endpoints:**
- `/fhir/**` — FHIR REST API (SMART-protected; `/fhir/metadata` is public)
- `/api/admin/**` — REST API for role/scope management (requires `SUPER_ADMIN` or `CLINICAL_ADMIN`)
- `/admin/**` — Thymeleaf admin UI (browser OAuth2 Authorization Code flow)
- `/swagger-ui.html` — OpenAPI docs

**Package layout** (`it.arsinfo.fhir`):
- `config/` — SecurityConfig, FhirServerConfig, RbacProperties
- `domain/entity/` — Role, RoleScope, UserRoleAssignment (JPA entities)
- `domain/enums/` — BuiltInRole
- `interceptor/` — SmartAuthorizationInterceptor, SmartRuleBuilder
- `security/jwt/` — JwtClaimsExtractor, SmartScopeParser, SmartScope types
- `security/rbac/` — RbacScopeResolver (merges JWT scopes + DB role scopes)
- `servlet/` — FhirRestfulServlet (registers HAPI servlet)
- `web/api/` — REST controllers for role/scope/user-role management
- `web/ui/` — Thymeleaf admin controllers
- `init/` — BuiltInRoleSeeder (seeds default roles at startup)

**Built-in roles** (seeded at startup, not deletable): `SUPER_ADMIN`, `CLINICAL_ADMIN`, `PRACTITIONER`, `NURSE`, `PATIENT`, `READONLY`.

## Key Configuration

Environment variables (see `.env.example`):

| Variable | Purpose |
|---|---|
| `AAA_ISSUER_URI` | JWT issuer / OIDC discovery root |
| `AAA_JWKS_URI` | JWKS endpoint for signature verification |
| `RBAC_ROLES_CLAIM` | JWT claim path for roles array (default: `realm_access.roles`) |
| `RBAC_SCOPE_CLAIM` | JWT claim for SMART scope string (default: `scope`) |
| `RBAC_PATIENT_CLAIM` | JWT claim for SMART patient context (default: `patient`) |
| `RBAC_DB_ROLES_ENABLED` | Resolve roles from local DB in addition to JWT (default: `true`) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL connection (prod profile) |

Spring profiles: `dev` (H2 + DEBUG logging), `prod` (PostgreSQL + hardened settings). Config files: `application.yml`, `application-dev.yml`, `application-prod.yml`.

Database schema is managed by Liquibase (`src/main/resources/db/changelog/`).
