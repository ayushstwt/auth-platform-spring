# Authentication & Token Design Architecture

This document describes the security architecture, cryptographic primitives, and token lifecycle management implemented in `auth-platform-spring`.

---

## 1. Token Model

### 1.1 Access Tokens (JWT)
- **Algorithm**: RS256 (Asymmetric RSA PKCS#1 v1.5 with SHA-256).
- **Format**: RFC 7519 JSON Web Token.
- **Validity**: Short-lived (Default: 15 minutes, configurable via `app.security.jwt.access-token-expiry-minutes`).
- **Header Claims**:
  - `alg`: `RS256`
  - `typ`: `JWT`
  - `kid`: Key Identifier uniquely identifying the signing public key in JWKS.
- **Payload Claims**:
  - `sub`: User ID (`id.toString()`)
  - `email`: Normalized user email address
  - `roles`: Set of assigned roles (`ROLE_USER`, `ROLE_ADMIN`)
  - `iss`: Token issuer (`auth-platform-spring`)
  - `aud`: Token audience (`auth-platform-spring-api`)
  - `jti`: Unique token UUID (prevents replay)
  - `iat`: Issued-at epoch seconds (UTC)
  - `exp`: Expiration epoch seconds (UTC)

### 1.2 Refresh Tokens (Opaque & Hashed)
- **Entropy**: Cryptographically secure 256-bit pseudo-random byte sequence generated via `java.security.SecureRandom`.
- **Transmission**: Base64 URL-safe string without padding.
- **Storage**: Tokens are **never** stored plaintext in the database. Only their one-way SHA-256 hex digest (`token_hash`) is persisted.
- **Validity**: Long-lived (Default: 7 days, configurable via `app.security.jwt.refresh-token-expiry-days`).

---

## 2. Refresh Token Rotation & Family-Based Theft Detection

To guard against refresh token interception and replay attacks, we employ **family-based token rotation**:

```
[Login] ──► Issues (AccessToken_1, RefreshToken_1, Family_A)
              │
[Refresh] ──► Submits RefreshToken_1
              ├── Marks RefreshToken_1 as REVOKED (replaced_by = hash(RefreshToken_2))
              └── Issues (AccessToken_2, RefreshToken_2, Family_A)
              │
[Reuse Attempt] ──► Attacker / client replays RefreshToken_1
                      ├── Server detects RefreshToken_1 is already revoked
                      ├── ACTION: The entire Family_A is immediately REVOKED
                      └── Audit log records TOKEN_REUSE_DETECTED
```

---

## 3. Account Lockout & Anti-Enumeration Protections

1. **Lockout Policy**:
   - Accounts are locked for 15 minutes after 5 consecutive failed login attempts (`app.security.auth.max-failed-attempts`, `app.security.auth.lockout-duration-minutes`).
   - Successful authentication resets the failed login counter.

2. **Constant-Time Non-Existent User Verification**:
   - When a non-existent email is submitted, the system performs a dummy BCrypt verification against a pre-computed hash.
   - This prevents timing side-channel attacks from leaking whether an account exists.

3. **Generic Responses**:
   - Auth failures return a uniform HTTP 401 with generic message `Invalid credentials` regardless of whether the email was wrong, the password was wrong, or the account is locked/disabled.

---

## 4. Key Generation & Dev Commands

To generate an RSA 2048-bit keypair for production use:

```bash
# 1. Generate private key (PEM PKCS#8)
openssl genpkey -algorithm RSA -out private_key.pem -pkeyopt rsa_keygen_bits:2048

# 2. Extract public key (PEM)
openssl rsa -pubout -in private_key.pem -out public_key.pem
```

Configure the file locations via environment variables:
```bash
JWT_PRIVATE_KEY_LOCATION=file:/path/to/private_key.pem
JWT_PUBLIC_KEY_LOCATION=file:/path/to/public_key.pem
```

*Note: In development and test environments, an in-memory 2048-bit RSA keypair is generated automatically upon startup if no external key paths are provided.*

---

## 5. JWKS Public Key Distribution

Public keys used for verifying access tokens are published at:
```http
GET /.well-known/jwks.json
```
Example Response:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "e": "AQAB",
      "kid": "auth-platform-key-4a9b2c8d",
      "n": "u1...[modulus]..."
    }
  ]
}
```
