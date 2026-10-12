# Multi-Factor Authentication (MFA) Design

This document details the Multi-Factor Authentication (MFA) architecture implemented in Phase 6 of the Ayshriv Auth Platform.

---

## 1. Architecture Overview

MFA is integrated uniformly across **all** login mechanisms (Password, Email OTP, Phone OTP, Google OAuth OIDC) through the central `AuthenticationResultService`.

When a user initiates Step 1 of authentication with MFA enabled:
1. `AuthenticationResultService` generates an intermediate **MFA token** (`mfaToken`).
2. The endpoint returns `200 OK` with:
   ```json
   {
     "status": "success",
     "message": "MFA verification required",
     "data": {
       "mfaRequired": true,
       "mfaToken": "eyJraWQi...",
       "availableFactors": ["TOTP", "EMAIL", "SMS", "RECOVERY_CODE"]
     }
   }
   ```
3. The client submits `POST /api/v1/auth/mfa/verify` with `{ mfaToken, factor, code }`.
4. Platform issues access & refresh tokens bearing `'amr'` claims (e.g. `["pwd", "mfa_totp"]`) and `'auth_time'` (Unix timestamp).

```
+-------------------------------------------------------------------------+
|                  Step 1 Authentication (Any Method)                    |
|         (Password, Email OTP, Phone OTP, Google OIDC)                   |
+------------------------------------+------------------------------------+
                                     |
                          MFA Enabled on User?
                                     |
               +---------------------+---------------------+
               | No                                        | Yes
               v                                           v
     +-------------------+                      +-----------------------+
     | Issue API Tokens  |                      | Return 200 OK with:   |
     | amr: ["pwd"]      |                      | - mfaRequired: true   |
     +-------------------+                      | - mfaToken            |
                                                | - availableFactors    |
                                                +-----------+-----------+
                                                            |
                                                            v
                                                +-----------------------+
                                                | Step 2: Client calls  |
                                                | /api/v1/auth/mfa/verify
                                                +-----------+-----------+
                                                            |
                                                   Verify Factor Code
                                                   (TOTP, Fallback, etc)
                                                            |
                                                            v
                                                +-----------------------+
                                                | Issue Final Tokens    |
                                                | amr: ["pwd","mfa_totp"]
                                                | auth_time: <epoch_sec>|
                                                +-----------------------+
```

---

## 2. Authentication Factors

### A. TOTP (Time-Based One-Time Password - RFC 6238)
- **Algorithm:** HMAC-SHA1 compatible with Google Authenticator, Microsoft Authenticator, Authy.
- **Digits & Step:** 6 digits, 30-second time step.
- **Clock Skew Window:** $\pm 1$ step (current step, previous step $-30$s, next step $+30$s).
- **Anti-Replay Protection:** The user's `totp_last_timestep` is updated in the database on verification. Subsequent verification attempts with a time-step $\le$ `totp_last_timestep` are strictly rejected.
- **Secret Encryption at Rest:** TOTP secrets are encrypted using **AES-256-GCM** before persistence in `users.totp_secret_encrypted`. Secrets are never returned to clients after initial setup.

### B. Fallback Factors (Email OTP & SMS OTP)
- Reuses the existing generic OTP engine with `purpose = MFA`.
- Channels are only permitted if verified (`email_verified = true` for Email, `phone_verified = true` for SMS).
- Fallback codes are sent via `POST /api/v1/auth/mfa/challenge/send` with `{ mfaToken, channel }`.

### C. Emergency Recovery Codes
- **Count:** 10 single-use codes generated at setup or regeneration.
- **Format:** Formatted as `XXXX-XXXX` from an unambiguous Base32 character set (excluding `0`, `O`, `1`, `I`).
- **Hashed at Rest:** Stored in `user_recovery_codes` table hashed with server-side pepper via HMAC-SHA256 (`code_hash`). Plain codes are displayed **once** upon generation and never stored.
- **Single-Use Enforcement:** Marked with `consumed_at` upon use; subsequent attempts with the same code fail.

---

## 3. AES-256-GCM Encryption & Key Rotation

### Payload Format
Encrypted TOTP secrets are stored in the database in self-describing versioned format:
```
v1:{keyId}:{iv_base64}:{ciphertext_base64}
```

### Key Rotation Strategy
- **Encryption:** Always uses the active key identified by `app.mfa.current-key-id` (e.g. `k1`).
- **Decryption:** Extracts `{keyId}` from the stored string and looks up the corresponding key from `app.mfa.encryption-keys`.
- To rotate keys:
  1. Add new key `k2` to `app.mfa.encryption-keys`.
  2. Set `app.mfa.current-key-id=k2`.
  3. Existing secrets encrypted under `k1` continue to decrypt seamlessly.

### Generating an AES-256 Key
Use OpenSSL to generate a 32-byte Base64 key:
```bash
openssl rand -base64 32
```
Or a 64-character hexadecimal key:
```bash
openssl rand -hex 32
```

---

## 4. MFA Intermediate Token Security

The `mfaToken` issued in Step 1 is a signed JWT with strict separation:
- **Audience:** `auth-platform-mfa` (the resource server requires `auth-platform-spring-api` for general endpoints). Calling regular APIs with an `mfaToken` returns HTTP `401 Unauthorized`.
- **Claim:** `token_type: "mfa_token"`.
- **Short TTL:** 5 minutes.
- **Brute Force Protection:** Failed verification attempts against an `mfaToken` are counted. If `maxAttempts` (default 5) is exceeded, the token is permanently locked.

---

## 5. Step-Up Authentication (`@RequiresRecentMfa`)

Sensitive operations require fresh authentication:
- `POST /api/v1/users/me/change-password`
- `POST /api/v1/users/me/phone/send-otp`
- `POST /api/v1/users/me/mfa/totp/setup`
- `POST /api/v1/users/me/mfa/totp/confirm`
- `POST /api/v1/users/me/mfa/disable`
- `POST /api/v1/users/me/mfa/recovery-codes/regenerate`

If the access token's `auth_time` is older than `stepUpValidityMinutes` (default 10-15 minutes), the request is rejected with:
```json
{
  "status": "error",
  "message": "Step-up authentication required: recent MFA or login required for this operation",
  "errorCode": "E031"
}
```

---

## 6. Admin Policy Enforcement

When `app.mfa.enforce-for-admin=true`:
- Admin endpoints (`/api/v1/admin/**`) check that the token contains an MFA factor in the `'amr'` claim (e.g. `mfa_totp`, `mfa_sms`, `mfa_email`, `mfa_recovery`).
- Admin tokens issued without completing MFA receive `403 Forbidden` with error code `E034`.

---

## 7. Audit Logging and User Notifications

- **Audit Actions:**
  - `MFA_SETUP` (`ATTEMPT`, `FAILURE`, `CHALLENGE_ISSUED`)
  - `MFA_ENABLE` (`SUCCESS`)
  - `MFA_DISABLE` (`SUCCESS`, `FAILURE`)
  - `MFA_VERIFY` (`SUCCESS`)
  - `MFA_FAILED` (`FAILURE`)
  - `RECOVERY_CODE_USED` (`SUCCESS`)
  - `RECOVERY_CODES_REGENERATED` (`SUCCESS`)
- **Email Notifications:**
  - Sent whenever MFA is enabled, disabled, or recovery codes are regenerated.
