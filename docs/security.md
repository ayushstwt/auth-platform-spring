# Security Architecture & Threat Model Review

This document describes the security controls, key rotation procedures, threat models, and the OWASP ASVS (Application Security Verification Standard) Level 2 compliance verification for `auth-platform-spring`.

---

## 1. Asset Catalog

The platform safeguards the following critical assets:
- **User Passwords**: Stored as salted BCrypt hashes (strength 12) with zxcvbn password policy enforcement. Plaintext passwords never reach storage or logs.
- **One-Time Passwords (OTPs)**: Ephemeral 6-digit codes hashed with HMAC-SHA256 and an environment secret pepper before persisting in PostgreSQL. Never logged in cleartext.
- **Refresh Tokens & Sessions**: Cryptographically strong 64-character hex tokens generated with `SecureRandom`, stored exclusively as SHA-256 hashes, tracked by token family with rotation and automatic reuse-detection invalidation.
- **Signing Keys (RSA)**: 2048-bit RSA key pairs (`RS256`) used for minting JWT access tokens. Public keys exposed in multi-key JWKS endpoint (`/.well-known/jwks.json`).
- **Federated Identity Identifiers**: Google OAuth subjects (`sub`) linked to verified email accounts.
- **User PII**: Email addresses and E.164 phone numbers. Masked in all application logging output (`u***r@example.com`, `+19******4567`).

---

## 2. Threat Modeling & Controls

| Threat Scenario | Description | Platform Defense & Controls | Status |
| :--- | :--- | :--- | :--- |
| **Credential Stuffing** | Automated brute-force login attempts across millions of leaked credentials. | 1. Progressive account lockout (max 5 failed attempts locks account for 15 minutes).<br>2. IP-based and group-based rate limiting (Bucket4j distributed Redis/in-memory limiter).<br>3. Generic error responses (`Invalid credentials`) to prevent enumeration.<br>4. Re-authentication requirement for sensitive operations. | **Mitigated** |
| **OTP Brute-Force** | Guessing 6-digit codes within the expiration window. | 1. Max 5 verification attempts per OTP challenge; invalidated upon threshold.<br>2. 60-second resend cooldown.<br>3. Max 5 sends per hour per destination and per IP.<br>4. Constant-time byte comparison (`MessageDigest.isEqual`) on HMAC hashes to defeat timing attacks. | **Mitigated** |
| **SMS Pumping / Toll Fraud** | Attackers trigger thousands of SMS verifications to premium-rate numbers. | 1. Geographic restrictions: strictly allowed country codes (`IN`, `US`, `GB`).<br>2. Blocked premium prefixes (+1900, +1976, etc.).<br>3. Multi-tier velocity limiters (per-phone, per-IP, per-user, global hourly cap).<br>4. Circuit breaker daily financial cost-guard limit blocking further sends on overrun. | **Mitigated** |
| **Token Theft & Replay** | Intercepted or exfiltrated access or refresh tokens. | 1. Short access token TTL (15 minutes).<br>2. Immediate access token revocation denylist (`jti` check via Caffeine cache & Redis).<br>3. Refresh token family rotation: each refresh burns the old token. Detection of already-used refresh token triggers revocation of the entire session family.<br>4. Session management endpoints (`DELETE /api/v1/sessions/{familyId}`). | **Mitigated** |
| **Account Takeover via OAuth** | Forged ID tokens or cross-account linking via rogue OAuth providers. | 1. Strict Google ID token verification (issuer `https://accounts.google.com`, audience check, JWKS signature verification, unexpired `exp`).<br>2. Email verification requirement in token payload.<br>3. Multi-factor reauth before unlinking providers. | **Mitigated** |
| **Account Enumeration** | Timing or message discrepancies revealing registered users. | 1. Uniform response messages for registration/login/password reset requests.<br>2. Consistent response latency matching to avoid timing discrepancies. | **Mitigated** |

---

## 3. Key Rotation Runbook (`kid` & JWKS)

The service supports zero-downtime RSA signing key rotation:
1. **Multi-Key JWKS**: The JWKS endpoint at `GET /.well-known/jwks.json` serves active and previous public keys mapped by unique `kid` (`auth-platform-key-{uuid}`).
2. **Key Rotation Execution**:
   - Calling `RsaKeyProvider.rotateKey()` generates a new RSA 2048-bit key pair and prepends it to the active key list.
   - New JWT access tokens are signed using the newly minted key and stamped with its `kid` header.
   - Resource servers and API gateways cache the JWKS and seamlessly verify existing tokens signed with previous keys until they naturally expire (15-minute TTL).
3. **Key Decommissioning**:
   - Previous keys can be retired after `JWT_ACCESS_TOKEN_EXPIRY_MINUTES` plus clock skew buffer has elapsed.

---

## 4. HTTP & Transport Hardening

- **HSTS**: `max-age=31536000; includeSubDomains; preload` enforced on HTTPS.
- **CSP & Framing**: `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'` and `X-Frame-Options: DENY`.
- **MIME Sniffing**: `X-Content-Type-Options: nosniff`.
- **Server Information**: Server header suppressed (`server.server-header=""`).
- **Body & Header Sizing**: Multipart and request body capped at 5MB; request headers capped at 16KB.
- **JSON Parser Constraints**: Jackson configured with `StreamReadConstraints` restricting max nesting depth to 64 and string lengths to 5MB to prevent deserialization exhaustion attacks.
- **Reverse Proxy Support**: `server.forward-headers-strategy=framework` ensures client IP extraction respects `X-Forwarded-For` behind trusted reverse proxies/load balancers.

---

## 5. OWASP ASVS Level 2 Verification Matrix

| ASVS ID | Verification Item | Platform Implementation / Control | Status |
| :--- | :--- | :--- | :--- |
| **V2.1.1** | Passwords submitted over encrypted transport | Enforced over TLS / HSTS with strict headers. | **PASS** |
| **V2.1.2** | Passwords can be at least 64 characters | Max length 128 characters supported, no truncation before hashing. | **PASS** |
| **V2.1.7** | Check against known breached passwords | Checked against zxcvbn password policy strength validator. | **PASS** |
| **V2.4.1** | Account credential brute force protection | 5 failed attempts locks account with backoff and audit alert. | **PASS** |
| **V2.8.1** | Multi-factor / OTP expiration & single use | Single-use hashed OTP with 5-minute TTL and 5 attempt max limit. | **PASS** |
| **V3.1.1** | Session tokens cryptographically random | 256-bit entropy generated using `java.security.SecureRandom`. | **PASS** |
| **V3.2.1** | Session token bind to token families & rotation | Refresh token rotation with immediate reuse detection and family invalidation. | **PASS** |
| **V3.3.1** | Session termination logout and revocation | JTI denylist for immediate access token revocation; session family revocation. | **PASS** |
| **V3.7.1** | Session concurrent limits and device tracking | Device name, IP address, and last used timestamps recorded on refresh token family. | **PASS** |
| **V8.1.1** | Security audit logging of authentication events | Structured UserLog audit record on logins, logouts, lockouts, OTP actions. | **PASS** |
| **V8.3.1** | Sensitive data masking in logs | Passwords, tokens, OTPs never logged; emails and phones masked. | **PASS** |
| **V13.1.1**| Strict API rate limiting | Distributed Redis / in-memory Bucket4j rate limiter with Retry-After header. | **PASS** |
| **V14.4.1**| HTTP Security Headers | Strict CSP, X-Frame-Options DENY, nosniff, strict CORS origin validation. | **PASS** |

---

## 6. Operability & Observability

- **Micrometer Metrics**: Exposes metrics including `auth.logins.success`, `auth.logins.failure`, `auth.otp.sent`, `auth.account.locked`, `auth.account.unlocked`, `auth.session.revoked`.
- **Health Indicators**: `mailServiceHealth`, `smsServiceHealth`, DB health, ping, with Kubernetes liveness and readiness probe groups.
- **Distributed Tracing**: `X-Request-Id` correlation ID propagated in MDC across synchronous filter chains and `@Async` thread pools via `AsyncConfig` TaskDecorator.
- **Log Formatting**: Standard readable text logging in `dev` and structured JSON logging (`logstash-logback-encoder`) in `prod` profile.
