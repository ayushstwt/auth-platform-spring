# OAuth 2.0 / OpenID Connect Design: Sign in with Google

## Architecture Overview
The platform supports Google Sign-In through verified OpenID Connect (OIDC) ID tokens for SPA/Native clients, and optional browser Authorization Code redirect flow with PKCE via Spring Security `oauth2Login`.

In all flows:
1. Google access/refresh tokens are **never returned to the client and never stored**.
2. Only our platform's RS256 JWT access tokens and hashed refresh tokens are issued to clients.
3. Federated identities are matched strictly by the immutable Google subject identifier (`sub`), **never by email alone**.

---

## Supported Client Authentication Flows

### Flow A: Native and SPA Clients (Recommended)
1. The frontend / mobile app uses Google Identity Services (GIS) or Google Sign-In SDK to authenticate the user and obtain a Google ID token.
2. The client sends the ID token to:
   ```http
   POST /api/v1/auth/google
   Content-Type: application/json

   {
     "idToken": "<google_id_token>"
   }
   ```
3. The server validates:
   - **Signature**: Verified against Google's public JWKS keys (`https://www.googleapis.com/oauth2/v3/certs`) using Nimbus JOSE/JWT.
   - **Issuer**: Must be `https://accounts.google.com` or `accounts.google.com`.
   - **Audience (`aud`)**: Must match our allow-list of authorized Google Client IDs (`app.google.client-ids`).
   - **Expiration (`exp`)**: Token must not be expired.
   - **Email verified**: The claim `email_verified` must be `true`.
   - **Hosted Domain (`hd`)**: If `app.google.hosted-domain` is configured, the `hd` claim must match.
4. Server executes the account-linking policy and returns `AuthResponse` with our JWT tokens.

---

### Flow B: Browser Redirect Flow (Authorization Code + PKCE)
1. Enabled via `app.google.redirect-flow-enabled: true`.
2. Initiated by navigating to `/oauth2/authorization/google`.
3. Standard Authorization Code with PKCE, `state`, and `nonce` verification handled by Spring Security.
4. On success, `OAuth2LoginSuccessHandler`:
   - Extracts verified OIDC claims.
   - Executes the account-linking policy.
   - Generates a cryptographically random, single-use **exchange code** (TTL 60s).
   - Redirects the browser to `${app.google.redirect-success-url}?code=<exchangeCode>` (**tokens are never exposed in URLs**).
5. Frontend exchanges the one-time code for platform tokens via:
   ```http
   POST /api/v1/auth/google/exchange
   Content-Type: application/json

   {
     "exchangeCode": "<one_time_exchange_code>"
   }
   ```

---

## Account-Linking Policy Table

| Scenario | Federated Identity Exists? | Local Account with Email Exists? | Local Account Email Verified? | Action Taken | Security Rationale |
| :--- | :---: | :---: | :---: | :--- | :--- |
| **1. Existing Linked Identity** | **Yes** (matched by Google `sub`) | Any | Any | **Log in directly.** Issues platform access and refresh tokens. | Google `sub` is permanent and uniquely bound to the account. |
| **2. Verified Local Account** | **No** | **Yes** | **Yes** (`email_verified = true`) | **Auto-link identity & notify.** Creates `federated_identities` record, issues platform tokens, and asynchronously sends a security alert email to the user. | Since both accounts proved ownership of the same verified email, linking is safe. User is alerted immediately. |
| **3. Unverified Local Account** | **No** | **Yes** | **No** (`email_verified = false`) | **Block login with HTTP 403 (`E023`).** Linking rejected until local email is verified. | **Pre-account takeover defense:** Prevents an attacker from registering an unverified account with victim's email, or claiming an unverified account via Google. |
| **4. New User** | **No** | **No** | N/A | **Create user & link.** User created with `email_verified = true`, `has_password = false`, a random unusable bcrypt hash, `ROLE_USER`, and linked Google identity. | Seamless onboarding. Password can be set anytime via `/api/v1/auth/password/reset`. |

---

## Identity Management & Last-Method Unlinking Rule

Users can view and manage their linked login providers under `/api/v1/users/me/identities`:
- `GET /api/v1/users/me/identities`: Lists linked identities.
- `POST /api/v1/users/me/identities/google/link`: Explicitly links a Google account.
- `DELETE /api/v1/users/me/identities/google`: Unlinks Google account.

> [!CAUTION]
> **Defense against Account Lockout (`E024`):**
> A user cannot unlink their Google identity if `has_password = false` and Google is their only linked authentication method. The user must first set a password or link another provider before unlinking.

---

## Setting Up Google Cloud OAuth Client

1. **Create / Select Project**: Go to [Google Cloud Console](https://console.cloud.google.com).
2. **OAuth Consent Screen**:
   - User Type: **External** (or Internal for Google Workspace).
   - App Name: `Ayshriv Auth Platform`.
   - Authorized domains: `ayshriv.com` (prod) or leave blank for local dev.
   - Scopes: `openid`, `profile`, `email`.
3. **Credentials -> Create Credentials -> OAuth Client ID**:
   - **Application Type**: Web application
   - **Name**: Auth Platform Web Client
   - **Authorized JavaScript origins**:
     - Dev: `http://localhost:3000`, `http://localhost:5173`, `http://localhost:8080`
     - Prod: `https://auth.ayshriv.com`, `https://app.ayshriv.com`
   - **Authorized redirect URIs**:
     - Dev: `http://localhost:8080/login/oauth2/code/google`
     - Prod: `https://auth.ayshriv.com/login/oauth2/code/google`
4. Copy Client ID and Client Secret into environment variables:
   - `GOOGLE_CLIENT_IDS=<Client_ID>`
   - `GOOGLE_OAUTH_CLIENT_ID=<Client_ID>`
   - `GOOGLE_OAUTH_CLIENT_SECRET=<Client_Secret>`
