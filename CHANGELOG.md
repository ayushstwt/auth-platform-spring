# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-10-12

### Added
- **Foundation & Standards**:
  - Unified envelope response contract (`ApiResponse<T>`, `ApiError`, `PageMetadata`).
  - Hierarchical business exception handling (`BaseException`, `ControllerExceptionHandler`).
  - Base entity pattern with UTC timestamp auditing and soft deletion (`@SQLRestriction`).
- **Authentication & Identity**:
  - Secure email/password registration and login with BCrypt (strength 12) and zxcvbn password policy enforcement.
  - Refresh token rotation with family-based reuse detection and revocation.
  - Multi-key RSA signing key provider with automatic key rotation and JWKS endpoint (`/.well-known/jwks.json`).
  - Token revocation denylist with in-memory Caffeine cache and Redis persistence.
- **Multi-Channel OTP**:
  - Multi-purpose OTP engine supporting email verification, password reset, and login.
  - HMAC-SHA256 hashed code storage with application secret pepper.
  - Phone OTP verification via Twilio with robust SMS abuse protection (prefix blocking, geo-fencing, multi-tier velocity caps, and daily cost guards).
- **Federated Identities**:
  - Google OAuth2 integration with OpenID Connect ID token verification.
  - Account linking and unlinking with safety controls.
- **Session & Device Management**:
  - Active session listing and revocation endpoints.
  - Device and IP tracking with new-device login email security alerts.
- **Account Lifecycle & Admin**:
  - Self-service email change with new-email verification.
  - User profile updates and soft-delete account functionality.
  - GDPR personal data export (`GET /api/v1/users/me/export`).
  - ShedLock-protected scheduled hard-purge job for soft-deleted accounts past retention.
  - Administrative endpoints for user search, lock/unlock, disable, force-logout, force-password-reset, and paginated audit query.
- **Security & Operability Hardening**:
  - Distributed rate limiting using Bucket4j (Redis-backed and in-memory).
  - Strict HTTP security headers (HSTS preload, CSP, X-Frame-Options DENY, nosniff, suppressed Server header).
  - Jackson stream read depth and string length constraints.
  - Forwarded headers support for reverse proxies and load balancers.
  - Comprehensive audit logging via `UserLog` with client IP capture and PII masking.
  - Micrometer metrics for logins, lockouts, revocations, and OTP actions.
  - Actuator health indicators for database, mail sender, and SMS provider.
  - `ProductionConfigGuard` failing fast on missing or insecure secrets during prod startup.
  - Complete CI/CD workflow, Dependabot, JaCoCo coverage check, and Maven enforcer.

---

### Initial Release
- Production-grade authentication and identity platform ready for enterprise deployment.
