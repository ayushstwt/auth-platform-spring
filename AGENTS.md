# AGENTS.md: auth-platform-spring

## Project
A reusable, production-grade authentication and identity platform by Ayshriv. Any developer must be able to clone it, set environment variables, and run it in production.
- Maven groupId: `com.ayshriv`, artifactId: `auth-platform-spring`
- Base package: `com.ayshriv.authplatform`

## Stack
Verify versions at start.spring.io and Maven Central before adding or changing anything. Do not guess versions.
- Latest Java LTS, Maven (wrapper committed)
- Spring Boot 4.x (4.0 or newer, never 3.x), Spring Security 7.x, Hibernate 7, Jakarta EE 11
- Boot 4 uses Jackson 3 (`tools.jackson` packages; annotations stay in `com.fasterxml.jackson.annotation`) and modularized starters. Use exactly the starter names start.spring.io generates.
- PostgreSQL, Spring Data JPA, Flyway (`ddl-auto=validate` everywhere except tests)
- Spring Security with the oauth2-resource-server starter (Nimbus JWT)
- Lombok only on non-entity classes; Java records for DTOs
- JUnit 5, Mockito, Spring Security Test, MockMvc
- NO Docker for now: no Dockerfile, docker-compose, or Testcontainers. Use a locally installed PostgreSQL (separate dev and test databases).

## Commands
- Build and test: `./mvnw verify`
- Run (dev): `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

## Standards (read `/docs/Best_practices_backend.md` and `/docs/BaseEntity.txt`)
- One response envelope: `ApiResponse<T>`, `ApiError`, `PageMetadata`; ISO-8601 `Instant` timestamps in UTC; HTTP status is the source of truth.
- Errors are thrown, never hand-built in controllers: `ErrorMessages` enum, one `BaseException` hierarchy, one `@RestControllerAdvice`. Catch-all returns a generic message, never `ex.getMessage()`. Spring's own 4xx must not become 500. Log expected 4xx at warn, 5xx at error.
- `BaseEntity` (package `com.ayshriv.authplatform.entity`): `Instant` timestamps, `@Getter`/`@Setter` (no `@Data`), ID-based `equals`/`hashCode`, `@JsonIgnore` that really hides active/deleted (with a serialization test), `@SQLRestriction("IS_DELETED = false")`, subclass callbacks call `super`.
- Controllers return DTOs, never entities. Validate every request body with Bean Validation.
- Audit logging through a `UserLog`-style entity with an enum action, no sensitive data, indexed on `(USER_ID, CREATED_ON)`.
- Packages under `com.ayshriv.authplatform`: `constants`, `config`, `controller`, `dto`, `entity`, `exception`, `repository`, `security`, `service`, `util`. Constructor injection only.

## Security rules (apply to every change)
- No secrets in code or committed config. Secrets come from environment variables; keep `.env.example` updated (no real values).
- Never log passwords, OTPs, tokens, or full PII. Mask emails and phone numbers.
- Store OTPs, refresh tokens, and recovery codes hashed only. Use `SecureRandom` and constant-time comparison.
- Generic auth failure messages to prevent account enumeration.
- Every new endpoint needs: an explicit authorization rule, input validation, a rate-limit decision, and tests for the unhappy paths.

## Git workflow
- Branch per phase from `develop`: `feature/01-foundation`, `feature/02-basic-auth`, `feature/03-email-otp`, `feature/04-phone-otp`, `feature/05-google-oauth`, `feature/06-mfa`, `feature/07-hardening`, `release/1.0.0`.
- Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `chore:`). Small, logical commits.
- Never commit directly to `main` or `develop`. PRs need passing tests.

## Working rules
- Start each phase with a short plan (files, tables, endpoints). Ask only if something is truly ambiguous.
- No placeholder or TODO code. Unfinished provider integrations go behind a clearly named interface.
- A phase is done only when: tests pass, README/docs are updated, and the acceptance checklist is reported item by item as pass/fail with known limitations listed.
- If a request conflicts with these rules, stop and ask.
- Do not touch code from earlier phases unless the current phase requires it, and say so when you do.

## Current status
- [x] Phase 1 Foundation
- [x] Phase 2 Basic auth
- [ ] Phase 3 Email OTP
- [ ] Phase 4 Phone OTP
- [ ] Phase 5 Google OAuth
- [ ] Phase 6 MFA
- [ ] Phase 7 Hardening
- [ ] Phase 8 Release
