# Operations Runbook: auth-platform-spring

This runbook outlines operational procedures, troubleshooting steps, and standard administrative routines for the authentication platform.

---

## 1. Startup & Health Checks

### Verification Steps
1. **Liveness Probe**:
   ```bash
   curl -f http://localhost:8080/actuator/health/liveness
   # Expect: {"status":"UP"}
   ```
2. **Readiness Probe**:
   ```bash
   curl -f http://localhost:8080/actuator/health/readiness
   # Expect: {"status":"UP"} (Verifies database connectivity)
   ```
3. **Public JWKS Verification**:
   ```bash
   curl -f http://localhost:8080/.well-known/jwks.json
   # Expect: {"keys":[{"kty":"RSA","kid":"...","use":"sig","alg":"RS256",...}]}
   ```

---

## 2. Common Failures & Triage

| Symptom | Probable Cause | Remediation Steps |
| :--- | :--- | :--- |
| **Startup Crash with `IllegalStateException: CRITICAL: Production configuration validation failed`** | Missing or dev-default secrets under `prod` profile. | Ensure `OTP_PEPPER` is ≥ 32 chars and `CORS_ALLOWED_ORIGINS` does not contain `localhost`. |
| **Users Receive 429 Too Many Requests** | Distributed rate limiter bucket exhausted. | 1. Check `Retry-After` header.<br>2. Check if a single NAT gateway/IP is aggregating requests without `X-Forwarded-For`.<br>3. Inspect Redis latency if `RATE_LIMIT_TYPE=redis`. |
| **Login returns `E004: Account locked`** | User exceeded maximum failed login attempts (5). | Wait for 15-minute lock duration or perform manual admin unlock (see Section 3). |
| **SMS Delivery Fails (`E015: Daily SMS limit exceeded`)** | Abuse protection cost-guard tripped. | Check logs for SMS toll fraud attempt. If valid traffic, reset daily cost counter via service or adjust `SMS_DAILY_COST_GUARD_LIMIT`. |

---

## 3. Administrative Procedures

### How to Unlock a Locked User
Admin users can unlock an account immediately via the API:
```bash
curl -X POST https://api.ayshriv.com/api/v1/admin/users/{userId}/unlock \
     -H "Authorization: Bearer <ADMIN_JWT>"
```
This resets the user status back to `ACTIVE` and clears `failed_login_count`.

### How to Force Password Reset on an Account
To force a password reset and revoke all existing sessions:
```bash
curl -X POST https://api.ayshriv.com/api/v1/admin/users/{userId}/force-password-reset \
     -H "Authorization: Bearer <ADMIN_JWT>"
```

### How to Purge Expired OTP Data
OTP cleanup runs automatically every 15 minutes via ShedLock (`OtpCleanupService`). To perform a manual ad-hoc database purge:
```sql
DELETE FROM otp_challenges WHERE expires_at < NOW() - INTERVAL '1 hour';
```

### How to Rotate RSA Signing Keys
RSA key rotation occurs with zero downtime:
1. Trigger key rotation programmatically via `RsaKeyProvider.rotateKey()`.
2. The newly generated key is added to the multi-key JWKS endpoint at `/.well-known/jwks.json` and becomes active for signing new access tokens.
3. Existing valid access tokens continue to verify against previous keys in the JWKS until their TTL expires (15 minutes).

---

## 4. Hotfix Process

When a critical bug or vulnerability must be patched in production:
1. **Branch from `main`**:
   ```bash
   git checkout main
   git pull origin main
   git checkout -b hotfix/<issue-name>
   ```
2. **Implement Fix & Test**:
   - Write tests reproducing and validating the fix.
   - Run `./mvnw verify` to ensure zero regressions and full coverage.
3. **Open PR to `main`**:
   - Submit PR with label `hotfix`.
   - Require code review and green CI checks.
4. **Tag Patch Release**:
   - Merge PR into `main` with a merge commit.
   - Create annotated tag for the patch version:
     ```bash
     git tag -a v1.0.1 -m "hotfix: resolve critical issue <issue-name>"
     git push origin v1.0.1
     ```
5. **Back-Merge into `develop`**:
   - Open PR from `main` into `develop` and merge to keep integration in sync.
