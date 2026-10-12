# SMS Providers & Abuse Prevention Design

## 1. Overview
Phase 4 extends the core authentication and identity platform to support mobile numbers and SMS verification by reusing the generic `OtpService` engine (with `channel = SMS`).

All mobile numbers are validated and stored strictly in **E.164 format** (e.g. `+919876543210`) using Google's `libphonenumber`.

---

## 2. Architecture & Design Decisions

### 2.1 Single Number vs `user_phones` Table
- We added `phone_e164` (VARCHAR(20), unique, indexed) and `phone_verified` (BOOLEAN) directly to the `users` table for Phase 4.
- **Justification**:
  - In consumer and enterprise single-sign-on (SSO) and identity providers, having a single primary verified phone number per user keeps authentication factors deterministic, clean, and avoids confusion about which number receives security alerts and MFA step-ups.
  - Adding `phone_e164` with a partial index or unique constraint prevents account hijacking and phone recycling attacks.
  - If multi-phone support (e.g., separate billing or emergency numbers) is ever required in future enterprise phases, an auxiliary `user_phones` table can easily be added without altering the primary auth credential.

### 2.2 Re-authentication Guard on Phone Change
- Changing an already verified phone number (`POST /api/v1/users/me/phone/send-otp`) requires a **recent login** (default within 10 minutes, configurable via `app.security.auth.reauth-validity-minutes`).
- If the last login was earlier, the API returns `401 Unauthorized` with error code `E020`, forcing the user to re-authenticate before modifying their recovery/SMS phone factor.

### 2.3 Provider Abstraction & Pluggability
The `SmsSender` interface decouples the platform from third-party vendor APIs:
- **`LoggingSmsSender`**: Active when `app.sms.provider=logging` (dev & test default). Logs the masked phone number and prints the OTP to the console.
- **`TwilioSmsSender`**: Active when `app.sms.provider=twilio`. Interacts with Twilio REST APIs to deliver SMS. Equipped with Resilience4j circuit breaker and retry with exponential backoff.
- **`TestSmsSender`**: Active under `@Profile("test")` for unit and MockMvc integration tests.

---

## 3. Abuse Controls & Toll Fraud Prevention
SMS pumping (or toll fraud) occurs when attackers trigger automated OTP sends to premium-rate numbers to extract revenue from telecommunication operators.

Phase 4 includes a multi-layered defense via `SmsAbuseProtectionService`:

1. **Blocked Prefix Filter**: Blocks premium rate and fraudulent prefixes (`app.sms.blocked-prefixes`, e.g., `+1900`, `+1976`, `+4490`).
2. **Country Code Allow-List**: Restricts SMS dispatch only to allowed country ISO codes (`app.sms.allowed-country-codes`, e.g., `IN, US, GB`).
3. **Multi-Bucket Rate Limiting (Bucket4j)**:
   - **Per-IP Rate Limit**: Max 20 sends/hour per IP.
   - **Per-User Rate Limit**: Max 5 sends/hour per user.
   - **Per-Phone Rate Limit**: Max 5 sends/hour per phone destination (enforced in DB query count).
   - **Global Hourly Cap**: Maximum 100 SMS messages per hour across the platform.
4. **Daily Cost-Guard Counter**:
   - Atomic counter (`app.sms.daily-cost-guard-limit`, default 500) that automatically shuts off SMS delivery and raises an `ERROR` alert if breached.
   - Reset automatically every midnight UTC via a scheduled cron job (`0 0 0 * * *`).

---

## 4. SMS Provider Setup Guides

### 4.1 Twilio Setup
1. Register an account at [twilio.com](https://www.twilio.com).
2. Obtain your **Account SID** and **Auth Token** from the Twilio Console.
3. Purchase an SMS-capable phone number or configure an alphanumeric Sender ID.
4. Set the following environment variables:
   ```bash
   SMS_PROVIDER=twilio
   TWILIO_ACCOUNT_SID=ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
   TWILIO_AUTH_TOKEN=your_auth_token_here
   TWILIO_FROM_PHONE_NUMBER=+15551234567
   ```

### 4.2 MSG91 (India) & DLT Compliance Notes
If integrating MSG91 in India:
- **DLT Registration**: TRAI (Telecom Regulatory Authority of India) mandates Distributed Ledger Technology (DLT) registration for sending SMS in India.
  1. Register on a DLT portal (e.g., Jio, Airtel, Vodafone Idea, BSNL).
  2. Register your Entity and obtain an **Entity ID**.
  3. Register your **Sender ID** (Header, e.g., `AYSHRV`).
  4. Register your **Content Template** for OTP (e.g., `Your verification code for Ayshriv Auth is {#var#}. Valid for 5 minutes.`).
  5. Acquire your **Template ID** (DLT Template ID).
  6. In MSG91 dashboard, configure your Authkey and map the DLT Template ID to the SMS flow.

---

## 5. Audit & Log Masking
- Phone numbers in audit trails and application logs are masked to protect PII:
  `+919876541234` $\rightarrow$ `+91******1234`
- Audit events logged for phone activities:
  - `PHONE_ADD`: First time a phone number is registered.
  - `PHONE_CHANGE`: Modification of an existing phone number.
  - `PHONE_VERIFY`: Confirmation of phone OTP code.
  - `LOGIN`: Phone OTP login attempt, success, or block.
