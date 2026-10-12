# Production Go-Live Checklist

This checklist must be reviewed and signed off prior to deploying `auth-platform-spring` to production environments.

---

## 1. TLS & Reverse Proxy / Load Balancer
- [ ] **TLS Termination**: All public traffic terminated over HTTPS using TLS 1.3 (or TLS 1.2 minimum) with strong cipher suites.
- [ ] **Forwarded Headers**: Load balancer configured to forward `X-Forwarded-For`, `X-Forwarded-Proto`, and `X-Forwarded-Host`.
- [ ] **Host Port Mapping**: Ensure client IP extraction matches upstream IP (`server.forward-headers-strategy=framework`).

---

## 2. Secrets Management & Environment Configuration
- [ ] **Zero Hardcoded Secrets**: Secrets populated strictly from AWS Secrets Manager, HashiCorp Vault, Kubernetes Secrets, or Doppler.
- [ ] **OTP Secret Pepper (`OTP_PEPPER`)**: Cryptographically random string with minimum 32 bytes (e.g. `openssl rand -hex 32`).
- [ ] **Database Credentials**: Unique `DB_USERNAME` and complex `DB_PASSWORD` created with least-privilege table permissions.
- [ ] **CORS Allowed Origins (`CORS_ALLOWED_ORIGINS`)**: Explicitly configured to match frontend domains (e.g. `https://app.ayshriv.com`). No wildcard (`*`) or `localhost` entries.

---

## 3. Database & Migrations
- [ ] **High Availability**: PostgreSQL provisioned with automated failover (e.g. Multi-AZ RDS / Cloud SQL).
- [ ] **Automated Backups**: Continuous archiving (WAL) and automated point-in-time recovery (PITR) enabled.
- [ ] **Flyway Migrations**: Applied during deploy pipeline or startup (`flyway.baseline-on-migrate=true`). `ddl-auto` verified set to `validate`.
- [ ] **Connection Pool**: HikariCP maximum pool size scaled to match database CPU core capacity (`DB_MAX_POOL_SIZE=20` default).

---

## 4. Email & SMS Provider Registration
- [ ] **Domain Authentication**:
  - [ ] **SPF**: TXT record published with authorized mail server IPs/includes.
  - [ ] **DKIM**: 2048-bit DKIM keys configured and verified with mail provider (SendGrid, SES, Mailgun).
  - [ ] **DMARC**: DMARC policy record (`v=DMARC1; p=reject; ...`) active.
- [ ] **SMS Sender ID / 10DLC**:
  - [ ] Carrier 10DLC campaign or Alphanumeric Sender ID registered with Twilio.
  - [ ] Emergency rate alerts and budget caps verified in Twilio console.

---

## 5. Google OAuth Production Credentials
- [ ] **OAuth Consent Screen**: Verified and moved from "Testing" to "In Production" status in Google Cloud Console.
- [ ] **Authorized Redirect URIs**: Explicitly updated with production callback URLs (e.g. `https://api.ayshriv.com/login/oauth2/code/google`).
- [ ] **Scopes**: Limited to `openid`, `profile`, `email`.

---

## 6. Observability, Metrics & Alerts
- [ ] **Prometheus Scraping**: Actuator `/actuator/prometheus` scraped at 15s intervals behind private network or auth.
- [ ] **Critical Alerting Rules**:
  - [ ] `auth.logins.failure` spike (> 50 / min) -> PagerDuty alert.
  - [ ] `smsServiceHealth` or `mailServiceHealth` DOWN -> Slack/Email alert.
  - [ ] Rate limit 429 spike (> 100 / min) -> Warning alert.
- [ ] **Centralized Logging**: Log output streamed to Datadog / Elasticsearch / CloudWatch with JSON formatting enabled (`logback-spring.xml` on `prod` profile). Log retention policy set according to compliance (e.g. 90-365 days).
