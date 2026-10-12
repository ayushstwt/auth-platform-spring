# OTP & Email Engine Architecture Design

## 1. Overview
The **OTP Engine** provides a reusable, hardened, and channel-agnostic verification system across all authentication flows:
- **Phase 3:** Email verification, passwordless email OTP login, password reset.
- **Phase 4:** Phone OTP via SMS.
- **Phase 6:** Multi-Factor Authentication (MFA) step-up.

---

## 2. Security Architecture & Threat Mitigations

### 2.1 Hashed OTP Storage with Server-Side Pepper
- Plaintext OTPs are **never stored in the database** and **never logged in production**.
- Each OTP is hashed using **HMAC-SHA256**:
  $$\text{hash} = \text{HMAC-SHA256}(\text{key} = \text{pepper}, \text{data} = \text{destination} + \text{":"} + \text{code})$$
- The `pepper` is loaded exclusively from environment variables (`OTP_PEPPER`) and never committed to source control.
- Even if a database snapshot is leaked, attackers cannot reverse the OTP codes without the server pepper.

### 2.2 Constant-Time Comparison
- Verification evaluates candidate codes using `MessageDigest.isEqual(...)`.
- This eliminates timing side-channel attacks that could allow attackers to guess digits iteratively.

### 2.3 Anti-Enumeration & Uniform Timing
- Endpoints like `/api/v1/auth/login/email-otp/send` and `/api/v1/auth/password/forgot` return the exact same generic success message regardless of whether the email exists or whether the user is active:
  > *"If the account exists, a one-time passcode has been sent"*
- In cases where the email address is not found in the database, a dummy HMAC-SHA256 calculation is computed to balance CPU time and maintain consistent response latency.

### 2.4 Multi-Tier Rate Limiting
1. **IP Rate Limit**: Monitored via Bucket4j (in-memory token bucket; 30 req/min for issuing, 60 req/min for verifying).
2. **Destination Rate Limit**: Max 5 OTP requests per hour per destination enforced both via Bucket4j and DB query counts.
3. **Resend Cooldown**: 60 seconds minimum interval between consecutive requests to the same destination/purpose.
4. **Single Active Challenge**: Requesting a new challenge immediately invalidates previously active challenges for that user/purpose.

### 2.5 Brute-Force & Attempt Exhaustion
- Challenges default to a maximum of **5 attempts**.
- On the 5th failed attempt, the challenge is deactivated and locked.
- Generic error response is returned for wrong/expired codes, preventing verification oracle leaks.

---

## 3. Database Schema

### `otp_challenges`
| Column | Type | Description |
|---|---|---|
| `id` | BIGSERIAL | Primary key |
| `created_on` | TIMESTAMPTZ | Creation timestamp |
| `updated_on` | TIMESTAMPTZ | Last update timestamp |
| `is_active` | BOOLEAN | Challenge active state |
| `is_deleted` | BOOLEAN | Soft delete flag |
| `user_id` | BIGINT (nullable) | Associated user (foreign key to `users`) |
| `channel` | VARCHAR(20) | `EMAIL`, `SMS` |
| `purpose` | VARCHAR(30) | `EMAIL_VERIFY`, `LOGIN`, `PASSWORD_RESET`, `PHONE_VERIFY`, `MFA` |
| `destination` | VARCHAR(255) | Normalized recipient (e.g., lowercased email) |
| `code_hash` | VARCHAR(64) | Hex-encoded HMAC-SHA256 hash |
| `expires_at` | TIMESTAMPTZ | Challenge expiration time (default 5 minutes) |
| `attempts` | INT | Number of failed verification attempts |
| `max_attempts`| INT | Maximum attempts permitted before invalidation (default 5) |
| `consumed_at` | TIMESTAMPTZ | Timestamp when code was successfully verified |
| `request_ip`  | VARCHAR(45) | IP address initiating the challenge |

### `shedlock`
Multi-node lock coordination table preventing duplicate runs of the background cleanup worker:
- `name` (VARCHAR(64), PK)
- `lock_until` (TIMESTAMPTZ)
- `locked_at` (TIMESTAMPTZ)
- `locked_by` (VARCHAR(255))

---

## 4. Email Delivery & Templates

### 4.1 Profiles
- **`dev` Profile**: `DevLoggingEmailSender` logs the OTP securely to the console/logger for rapid local testing without requiring an SMTP relay.
- **`prod` Profile**: `SmtpEmailSender` routes through JavaMailSender via a bounded asynchronous thread pool (`ThreadPoolTaskExecutor`) with 3 retry attempts and exponential backoff.
- **`test` Profile**: `TestEmailSender` captures codes in-memory for assertion tests.

### 4.2 Templates
HTML and plain-text templates rendered with Thymeleaf:
- `templates/mail/verify-email.html` & `.txt`
- `templates/mail/login-otp.html` & `.txt`
- `templates/mail/password-reset.html` & `.txt`

---

## 5. Production Domain Authentication (SPF, DKIM, DMARC)
When sending transactional emails in production:
1. **SPF (Sender Policy Framework)**: Publish a TXT record for your domain authorizing your SMTP provider's IP/hosts:
   `v=spf1 include:_spf.smtp-provider.com ~all`
2. **DKIM (DomainKeys Identified Mail)**: Sign outgoing emails with a 2048-bit RSA key and publish the matching public key TXT record at `<selector>._domainkey.<domain>`.
3. **DMARC (Domain-based Message Authentication)**: Ensure domain alignment with SPF/DKIM:
   `v=DMARC1; p=reject; pct=100; rua=mailto:dmarc-reports@yourdomain.com`
