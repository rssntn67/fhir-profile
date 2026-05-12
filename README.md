# FHIR RBAC Profile Server

HAPI FHIR JPA Server 8.x wrapped with a production-grade RBAC layer compliant with the
[SMART on FHIR App Launch IG](https://hl7.org/fhir/smart-app-launch/).

JWT validation is **provider-agnostic** — switch between Keycloak, Auth0, Okta, Azure AD,
or AWS Cognito with a single YAML change. Roles and SMART scopes live in a local database
and are managed through a REST API or a Thymeleaf admin UI.

---

## Table of Contents

1. [Quick Start (dev)](#quick-start-dev)
2. [Configuration](#configuration)
3. [Built-in Roles](#built-in-roles)
4. [Role & Scope Management — API Examples](#role--scope-management--api-examples)
   - [Obtain an admin token](#1-obtain-an-admin-token-keycloak)
   - [List roles](#2-list-all-roles)
   - [Create a custom role](#3-create-a-custom-role)
   - [Add a SMART scope to a role](#4-add-a-smart-scope-to-a-role)
   - [Remove a scope from a role](#5-remove-a-scope-from-a-role)
   - [Assign a role to a user](#6-assign-a-role-to-a-user)
   - [List a user's role assignments](#7-list-a-users-role-assignments)
   - [Revoke a role assignment](#8-revoke-a-role-assignment)
5. [Admin UI](#admin-ui)
6. [SMART Scope Reference](#smart-scope-reference)
7. [Provider Configuration](#provider-configuration)
8. [Architecture Notes](#architecture-notes)

---

## Quick Start (dev)

```bash
# 1. Start Keycloak + PostgreSQL
docker-compose up -d

# 2. Run with the H2 in-memory dev profile (default)
mvn spring-boot:run

# 3. Check the FHIR capability statement (no auth required)
curl http://localhost:8080/fhir/metadata

# 4. Open the Swagger UI
open http://localhost:8080/swagger-ui.html

# 5. Open the admin UI
open http://localhost:8080/admin/roles
```

Built-in roles are seeded automatically at startup (idempotent — safe to restart).

---

## Configuration

All settings are driven by environment variables; no code changes are required to switch
providers.

| Variable | Default | Purpose |
|---|---|---|
| `AAA_ISSUER_URI` | `http://localhost:8180/realms/fhir` | JWT issuer / OIDC discovery root |
| `AAA_JWKS_URI` | `http://localhost:8180/realms/fhir/protocol/openid-connect/certs` | JWKS endpoint for signature verification |
| `RBAC_ROLES_CLAIM` | `realm_access.roles` | Dot-notation path to the roles array in the JWT |
| `RBAC_SCOPE_CLAIM` | `scope` | JWT claim containing the SMART scope string |
| `RBAC_PATIENT_CLAIM` | `patient` | JWT claim with the SMART patient context ID |
| `RBAC_DB_ROLES_ENABLED` | `true` | Also resolve roles from the local DB |

### Switching providers (YAML only)

| Provider | `AAA_JWKS_URI` | `RBAC_ROLES_CLAIM` |
|---|---|---|
| Keycloak realm | `https://<host>/realms/<realm>/protocol/openid-connect/certs` | `realm_access.roles` |
| Keycloak client | `https://<host>/realms/<realm>/protocol/openid-connect/certs` | `resource_access.<client>.roles` |
| Auth0 | `https://<domain>/.well-known/jwks.json` | `roles` |
| Okta | `https://<domain>/oauth2/default/v1/keys` | `groups` |
| Azure AD | `https://login.microsoftonline.com/<tid>/discovery/v2.0/keys` | `roles` |
| AWS Cognito | `https://cognito-idp.<region>.amazonaws.com/<poolId>/.well-known/jwks.json` | `cognito:groups` |

---

## Built-in Roles

Seeded at startup from `BuiltInRole` enum. They cannot be deleted.

| Role | Context | SMART Scopes |
|---|---|---|
| `SUPER_ADMIN` | SYSTEM | `system/*.cruds` |
| `CLINICAL_ADMIN` | USER | `user/*.read`, `user/Patient.cruds`, `user/Practitioner.cruds`, `user/Organization.cruds`, `user/Location.cruds` |
| `PRACTITIONER` | USER | `user/Patient.read`, `user/Observation.cruds`, `user/Condition.cruds`, `user/Procedure.cruds`, `user/MedicationRequest.cruds`, `user/MedicationStatement.cruds`, `user/DiagnosticReport.cruds`, `user/Encounter.cruds`, `user/AllergyIntolerance.cruds`, `user/Immunization.cruds`, `user/CarePlan.cruds` |
| `NURSE` | USER | `user/Patient.read`, `user/Observation.cruds`, `user/Condition.read`, `user/MedicationRequest.read`, `user/MedicationStatement.read`, `user/AllergyIntolerance.read`, `user/Immunization.cruds`, `user/Encounter.read` |
| `PATIENT` | PATIENT | `patient/Patient.read`, `patient/Observation.read`, `patient/Condition.read`, `patient/MedicationRequest.read`, `patient/MedicationStatement.read`, `patient/DiagnosticReport.read`, `patient/AllergyIntolerance.read`, `patient/Immunization.read`, `patient/Encounter.read`, `patient/CarePlan.read` |
| `READONLY` | USER | `user/*.read` |

---

## Role & Scope Management — API Examples

All admin endpoints require a JWT bearer token. The caller must have the `SUPER_ADMIN` or
`CLINICAL_ADMIN` role (from the JWT or from a DB assignment). Delete operations require
`SUPER_ADMIN`.

Base URL: `http://localhost:8080`

---

### 1. Obtain an admin token (Keycloak)

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/fhir/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=fhir-server" \
  -d "username=admin" \
  -d "password=admin" \
  | jq -r .access_token)

echo $TOKEN   # paste into Authorization: Bearer <token>
```

---

### 2. List all roles

```bash
curl -s http://localhost:8080/api/admin/roles \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Response:**
```json
[
  {
    "id": 1,
    "name": "SUPER_ADMIN",
    "description": "Full unrestricted system access",
    "builtIn": true,
    "smartContext": "SYSTEM",
    "scopeCount": 1,
    "createdAt": "2026-05-12T10:00:00Z",
    "updatedAt": "2026-05-12T10:00:00Z"
  },
  {
    "id": 5,
    "name": "PATIENT",
    "description": "Patient self-access — read own compartment only",
    "builtIn": true,
    "smartContext": "PATIENT",
    "scopeCount": 10,
    "createdAt": "2026-05-12T10:00:00Z",
    "updatedAt": "2026-05-12T10:00:00Z"
  }
]
```

---

### 3. Create a custom role

Role names must be uppercase letters, digits, or underscores (`^[A-Z0-9_]+$`).

```bash
curl -s -X POST http://localhost:8080/api/admin/roles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "RADIOLOGIST",
    "description": "Radiology staff — read patient demographics, full CRUD on imaging studies",
    "smartContext": "USER"
  }' | jq .
```

**Response (`201 Created`):**
```json
{
  "id": 7,
  "name": "RADIOLOGIST",
  "description": "Radiology staff — read patient demographics, full CRUD on imaging studies",
  "builtIn": false,
  "smartContext": "USER",
  "scopeCount": 0,
  "createdAt": "2026-05-12T14:30:00Z",
  "updatedAt": "2026-05-12T14:30:00Z"
}
```

> Note the returned `id` (here `7`) — you will use it in the scope and assignment calls below.

---

### 4. Add a SMART scope to a role

```bash
# Add read access to Patient demographics
curl -s -X POST http://localhost:8080/api/admin/roles/7/scopes \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "scopeString": "user/Patient.read",
    "description": "Read patient demographics"
  }' | jq .

# Add full CRUD on ImagingStudy
curl -s -X POST http://localhost:8080/api/admin/roles/7/scopes \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "scopeString": "user/ImagingStudy.cruds",
    "description": "Full access to imaging studies"
  }' | jq .

# Read-only on DiagnosticReport
curl -s -X POST http://localhost:8080/api/admin/roles/7/scopes \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "scopeString": "user/DiagnosticReport.rs",
    "description": "Read and search diagnostic reports"
  }' | jq .
```

**Response (`201 Created`):**
```json
{
  "id": 23,
  "roleId": 7,
  "scopeString": "user/ImagingStudy.cruds",
  "description": "Full access to imaging studies"
}
```

#### List all scopes for a role

```bash
curl -s http://localhost:8080/api/admin/roles/7/scopes \
  -H "Authorization: Bearer $TOKEN" | jq .
```

```json
[
  { "id": 21, "roleId": 7, "scopeString": "user/Patient.read",       "description": "Read patient demographics" },
  { "id": 22, "roleId": 7, "scopeString": "user/ImagingStudy.cruds", "description": "Full access to imaging studies" },
  { "id": 23, "roleId": 7, "scopeString": "user/DiagnosticReport.rs","description": "Read and search diagnostic reports" }
]
```

---

### 5. Remove a scope from a role

Use the scope's `id` returned above (e.g. `21`):

```bash
curl -s -X DELETE http://localhost:8080/api/admin/roles/7/scopes/21 \
  -H "Authorization: Bearer $TOKEN"
# 204 No Content
```

---

### 6. Assign a role to a user

The `userId` path segment is the user's JWT **subject** (`sub` claim) — typically a UUID in
Keycloak. The `roleId` query parameter is the role's database ID.

```bash
# Find the user's subject from their token:
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq .sub

# Assign the RADIOLOGIST role (id=7) to user abc-123-def
curl -s -X POST \
  "http://localhost:8080/api/admin/users/abc-123-def-456/roles?roleId=7" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**Response (`201 Created`):**
```json
{
  "id": 42,
  "subject": "abc-123-def-456",
  "roleId": 7,
  "roleName": "RADIOLOGIST",
  "assignedBy": "admin-user-subject",
  "assignedAt": "2026-05-12T14:45:00Z",
  "revokedAt": null,
  "active": true
}
```

#### Assign a built-in role

```bash
# Assign PRACTITIONER (id=3) to a clinician
curl -s -X POST \
  "http://localhost:8080/api/admin/users/dr-smith-uuid/roles?roleId=3" \
  -H "Authorization: Bearer $TOKEN" | jq .

# Assign PATIENT (id=5) to a patient — compartment access is then bound
# to the 'patient' claim in their own JWT at request time
curl -s -X POST \
  "http://localhost:8080/api/admin/users/patient-uuid-789/roles?roleId=5" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

### 7. List a user's role assignments

```bash
curl -s http://localhost:8080/api/admin/users/abc-123-def-456/roles \
  -H "Authorization: Bearer $TOKEN" | jq .
```

```json
[
  {
    "id": 42,
    "subject": "abc-123-def-456",
    "roleId": 7,
    "roleName": "RADIOLOGIST",
    "assignedBy": "admin-user-subject",
    "assignedAt": "2026-05-12T14:45:00Z",
    "revokedAt": null,
    "active": true
  }
]
```

---

### 8. Revoke a role assignment

Use the assignment `id` (e.g. `42`). Requires `SUPER_ADMIN`.

```bash
curl -s -X DELETE \
  "http://localhost:8080/api/admin/users/abc-123-def-456/roles/42" \
  -H "Authorization: Bearer $TOKEN"
# 204 No Content
```

The assignment record is soft-deleted (`revokedAt` is set, `active` becomes `false`). The
user immediately loses the role on their next request.

---

## Admin UI

The Thymeleaf admin UI is available at `/admin` and mirrors all REST operations.

| URL | Purpose |
|---|---|
| `GET /admin/roles` | Table of all roles; builtIn badge; scope count |
| `GET /admin/roles/new` | Create a custom role |
| `GET /admin/roles/{id}/edit` | Edit name/description (locked for built-in roles) |
| `GET /admin/roles/{id}/scopes` | Add/remove SMART scopes with quick-pick buttons |
| `GET /admin/users` | Paginated user list with assigned roles |
| `GET /admin/users/{userId}/roles` | Assign or revoke roles for a specific user |

Requires a JWT bearer token with `SUPER_ADMIN` or `CLINICAL_ADMIN` role.

---

## SMART Scope Reference

### Format

```
<context>/<ResourceType>.<permissions>
```

| Part | Values |
|---|---|
| `context` | `patient`, `user`, `system` |
| `ResourceType` | Any FHIR R4 resource type, or `*` for all |
| `permissions` (v2) | Any combination of `c` (create), `r` (read), `u` (update), `d` (delete), `s` (search) |
| `permissions` (v1 legacy) | `read` → `rs`, `write` → `cud` |

### Examples

| Scope | Meaning |
|---|---|
| `system/*.cruds` | Full access to all resources (superuser) |
| `user/*.read` | Read + search all resources, any patient |
| `user/Patient.cruds` | Full CRUD on Patient resources, any patient |
| `user/Observation.rs` | Read and search Observations only |
| `patient/Observation.cruds` | Full CRUD on Observations within the caller's own patient compartment |
| `patient/MedicationRequest.read` | Read own prescriptions (legacy v1 — equivalent to `rs`) |

### PATIENT context and compartment binding

When a scope uses the `patient` context, access is automatically restricted to resources in
the patient compartment identified by the `patient` JWT claim. If the JWT has no `patient`
claim, the request is denied even if the role allows the scope.

```
JWT: { "sub": "user-uuid", "patient": "Patient/42", "scope": "patient/Observation.rs" }
→ HAPI rule: allow read/search on Observation inCompartment("Patient", "Patient/42")
```

---

## Architecture Notes

```
HTTP request
  └─ Spring Security (JWT validation via JWKS URI)
       └─ SmartAuthorizationInterceptor (HAPI hook)
            ├─ RbacScopeResolver
            │    ├─ JWT scope claim  →  SmartScopeParser
            │    ├─ JWT roles claim  →  DB role scopes  →  SmartScopeParser
            │    └─ DB user-role assignments  →  DB role scopes  →  SmartScopeParser
            └─ SmartRuleBuilder
                 └─ List<IAuthRule>  →  HAPI AuthorizationInterceptor.authorize()
```

**Two Spring Security filter chains:**
- `/fhir/**`, `/api/admin/**` — stateless, CSRF disabled, JWT bearer
- `/admin/**` — session-aware for Thymeleaf CSRF forms, JWT bearer

**Effective scope resolution** merges three sources (highest privilege wins):
1. Scopes in the JWT `scope` claim
2. Scopes from DB roles whose names match the JWT roles claim
3. Scopes from DB roles assigned to the user's `sub` in `fhir_user_role_assignments`
