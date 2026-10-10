# User Service (auth-platform-spring)

A reusable, production-grade identity and user-management platform built with Spring Boot and PostgreSQL.

---

## 1. Prerequisites

- **Java Development Kit**: Java 21 LTS (e.g., Eclipse Adoptium Temurin 21)
- **Apache Maven**: Maven 3.8+ (or use the committed `./mvnw` wrapper)
- **PostgreSQL**: PostgreSQL 15+ installed and running locally on port 5432

---

## 2. Database Setup

Create separate databases for local development and test execution:

```sql
-- Connect via psql
psql -U postgres

-- Create development database
CREATE DATABASE userservice_dev;

-- Create testing database
CREATE DATABASE userservice_test;
```

---

## 3. Environment Variables Configuration

Copy `.env.example` to `.env` or export environment variables in your environment.

| Variable | Description | Default (Dev) | Prod Required |
|---|---|---|---|
| `SERVER_PORT` | HTTP Server port | `8080` | No |
| `DB_HOST` | PostgreSQL host | `localhost` | Yes |
| `DB_PORT` | PostgreSQL port | `5432` | Yes |
| `DB_NAME` | PostgreSQL database name | `userservice_dev` | Yes |
| `DB_USERNAME` | PostgreSQL username | `postgres` | Yes |
| `DB_PASSWORD` | PostgreSQL password | `postgres` | Yes |
| `DB_MAX_POOL_SIZE` | HikariCP max connection pool size | `10` | No |
| `DB_MIN_IDLE` | HikariCP min idle connections | `2` | No |
| `TEST_DB_HOST` | PostgreSQL test database host | `localhost` | No (defaults to memory/local test) |
| `TEST_DB_PORT` | PostgreSQL test database port | `5432` | No |
| `TEST_DB_NAME` | PostgreSQL test database name | `userservice_test` | No |
| `TEST_DB_USERNAME` | PostgreSQL test database user | `postgres` | No |
| `TEST_DB_PASSWORD` | PostgreSQL test database password | `postgres` | No |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed origins | `http://localhost:3000,http://localhost:5173` | Yes |
| `CORS_ALLOWED_METHODS` | HTTP methods allowed | `GET,POST,PUT,PATCH,DELETE,OPTIONS` | No |
| `CORS_ALLOWED_HEADERS` | Allowed request headers | `Authorization,Content-Type,X-Request-Id,Accept,Origin` | No |
| `CORS_ALLOW_CREDENTIALS` | Allow cookies / credentials | `true` | No |
| `CORS_MAX_AGE_SECONDS` | Preflight max age in seconds | `3600` | No |

---

## 4. Build and Run Commands

### Run Tests and Code Verification
```bash
./mvnw verify
```

### Run Locally with Development Profile
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Swagger UI / OpenAPI (Dev Only)
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI Specification: `http://localhost:8080/v3/api-docs`

---

## 5. Architectural Standards

### Unified API Response Envelope (`ApiResponse<T>`)
Every endpoint returns a consistent JSON envelope:

#### Success Response
```json
{
  "status": "success",
  "message": "Service is healthy",
  "data": {
    "service": "user-service",
    "status": "UP",
    "timestamp": "2026-10-11T01:15:00Z"
  },
  "timestamp": "2026-10-11T01:15:00.123456Z"
}
```

#### Error Response
```json
{
  "status": "error",
  "message": "Validation failed",
  "errorCode": "E005",
  "data": null,
  "errors": [
    {
      "field": "email",
      "message": "must be a well-formed email address"
    }
  ],
  "path": "/api/v1/users",
  "timestamp": "2026-10-11T01:15:00.123456Z"
}
```

### Error Code Reference (`ErrorMessages`)
- `E001`: Resource not found (404)
- `E002`: User with identifier %s not found (404)
- `E003`: Resource already exists (409)
- `E004`: Authentication required (401)
- `E005`: Validation failed (400)
- `E006`: Access denied (403)
- `E007`: Malformed or unreadable request payload (400)
- `E008`: Request method not supported (405)
- `E009`: Media type not supported (415)
- `E010`: Missing required parameter (400)
- `E011`: Invalid parameter type (400)
- `E012`: Invalid credentials (401)
- `E013`: Token expired or invalid (401)
- `E500`: An unexpected internal error occurred (500)

### Request Tracing & Correlation ID
Every HTTP request is assigned or preserves a unique `X-Request-Id` correlation identifier, which is placed in SLF4J MDC (`correlationId`), included in console log patterns, and propagated in response headers.

---

## 6. Project Roadmap Status

- [x] **Phase 1: Foundation** (`feature/01-foundation` -> `v0.1.0`)
- [x] **Phase 2: Basic Auth** (`feature/02-basic-auth` -> `v0.2.0`)
- [ ] **Phase 3: Email OTP** (`feature/03-email-otp` -> `v0.3.0`)
- [ ] **Phase 4: Phone OTP** (`feature/04-phone-otp` -> `v0.4.0`)
- [ ] **Phase 5: Google OAuth** (`feature/05-google-oauth` -> `v0.5.0`)
- [ ] **Phase 6: MFA** (`feature/06-mfa` -> `v0.6.0`)
- [ ] **Phase 7: Hardening** (`feature/07-hardening` -> `v0.7.0`)
- [ ] **Phase 8: Release** (`release/1.0.0` -> `v1.0.0`)

---

## 7. Authentication Endpoints & Examples

### 1. Register a new user
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jane.doe@example.com",
    "password": "StrongPassword123!",
    "firstName": "Jane",
    "lastName": "Doe"
  }'
```

### 2. Login
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jane.doe@example.com",
    "password": "StrongPassword123!"
  }'
```

### 3. Rotate Refresh Token
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<REFRESH_TOKEN>"
  }'
```

### 4. Get Current User Profile
```bash
curl -X GET http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### 5. Change Password
```bash
curl -X POST http://localhost:8080/api/v1/users/me/change-password \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "currentPassword": "StrongPassword123!",
    "newPassword": "BrandNewPassword123!"
  }'
```

### 6. Public JWKS Key Set
```bash
curl -X GET http://localhost:8080/.well-known/jwks.json
```

