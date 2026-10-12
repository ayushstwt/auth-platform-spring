package com.ayshriv.authplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates critical production configurations during application startup in the 'prod' profile.
 * Fails fast with an IllegalStateException if required secrets, CORS origins, or credentials
 * are missing or still using insecure development defaults.
 */
@Component
@Profile("prod")
public class ProductionConfigGuard implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ProductionConfigGuard.class);

    private final AppProperties appProperties;
    private final Environment environment;

    public ProductionConfigGuard(AppProperties appProperties, Environment environment) {
        this.appProperties = appProperties;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        validateProductionSettings();
    }

    public void validateProductionSettings() {
        List<String> violations = new ArrayList<>();

        // 1. Verify OTP Pepper
        String otpPepper = appProperties.otp().pepper();
        if (otpPepper == null || otpPepper.isBlank()) {
            violations.add("OTP pepper (OTP_PEPPER) is missing");
        } else if (otpPepper.contains("default-otp-secret-pepper-change-in-production")
                || otpPepper.contains("test-pepper")
                || otpPepper.length() < 32) {
            violations.add("OTP pepper (OTP_PEPPER) is set to an insecure default or is shorter than 32 characters");
        }

        // 2. Verify CORS Allowed Origins
        List<String> origins = appProperties.security().cors().allowedOrigins();
        if (origins == null || origins.isEmpty()) {
            violations.add("CORS allowed origins (CORS_ALLOWED_ORIGINS) cannot be empty in production");
        } else {
            boolean hasLocalhost = origins.stream().anyMatch(o -> o.contains("localhost") || o.contains("127.0.0.1"));
            if (hasLocalhost) {
                violations.add("CORS allowed origins contain localhost/127.0.0.1 which is insecure for production");
            }
        }

        // 3. Verify Database Credentials
        String dbUser = environment.getProperty("spring.datasource.username");
        String dbPass = environment.getProperty("spring.datasource.password");
        if (dbUser == null || dbUser.isBlank() || "postgres".equalsIgnoreCase(dbUser) || "sa".equalsIgnoreCase(dbUser)) {
            violations.add("Database username is missing or using default 'postgres'/'sa'");
        }
        if (dbPass == null || dbPass.isBlank() || "postgres".equalsIgnoreCase(dbPass) || "password".equalsIgnoreCase(dbPass)) {
            violations.add("Database password is missing or set to an insecure default");
        }

        // 4. Verify SMS Provider if provider is twilio
        String smsProvider = appProperties.sms().provider();
        if ("twilio".equalsIgnoreCase(smsProvider)) {
            AppProperties.TwilioProperties twilio = appProperties.sms().twilio();
            if (twilio == null || twilio.accountSid() == null || twilio.accountSid().isBlank()) {
                violations.add("Twilio SMS provider selected but TWILIO_ACCOUNT_SID is missing");
            }
            if (twilio == null || twilio.authToken() == null || twilio.authToken().isBlank()) {
                violations.add("Twilio SMS provider selected but TWILIO_AUTH_TOKEN is missing");
            }
            if (twilio == null || twilio.fromPhoneNumber() == null || twilio.fromPhoneNumber().isBlank()) {
                violations.add("Twilio SMS provider selected but TWILIO_FROM_PHONE_NUMBER is missing");
            }
        }

        // 5. Verify Google OAuth Credentials if redirect flow is enabled
        if (appProperties.google().redirectFlowEnabled()) {
            String googleClientId = environment.getProperty("spring.security.oauth2.client.registration.google.client-id");
            String googleClientSecret = environment.getProperty("spring.security.oauth2.client.registration.google.client-secret");
            if (googleClientId == null || googleClientId.isBlank() || googleClientId.contains("dummy-google-client-id")) {
                violations.add("Google OAuth enabled but GOOGLE_OAUTH_CLIENT_ID is missing or set to dummy value");
            }
            if (googleClientSecret == null || googleClientSecret.isBlank() || googleClientSecret.contains("dummy-google-client-secret")) {
                violations.add("Google OAuth enabled but GOOGLE_OAUTH_CLIENT_SECRET is missing or set to dummy value");
            }
        }

        if (!violations.isEmpty()) {
            String errorMessage = "CRITICAL: Production configuration validation failed with " + violations.size() + " issue(s):\n - "
                    + String.join("\n - ", violations);
            log.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        log.info("Production configuration validated successfully. All required secrets and production settings verified.");
    }
}
