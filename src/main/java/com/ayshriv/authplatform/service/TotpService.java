package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

/**
 * TOTP generator and validator according to RFC 6238 (SHA-1, 6 digits, 30s step, +/- 1 step window).
 * Enforces replay prevention by verifying against the last used time-step.
 */
@Service
public class TotpService {

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int DIGIT_MODULO = 1_000_000;
    private static final int SECRET_BYTE_LENGTH = 20; // 160 bits (recommended for SHA1)
    private static final int WINDOW_STEPS = 1; // +/- 1 step (current, -1, +1)

    private final AppProperties appProperties;
    private final Base32 base32 = new Base32();
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Generates a new random Base32 TOTP secret key.
     */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return base32.encodeToString(bytes).replace("=", "");
    }

    /**
     * Builds standard otpauth URI for QR codes (RFC 6238 / Google Authenticator compatible).
     * Format: otpauth://totp/{issuer}:{email}?secret={secret}&issuer={issuer}&algorithm=SHA1&digits=6&period=30
     */
    public String buildOtpAuthUri(String email, String secret) {
        String issuer = appProperties.mfa().issuer();
        String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8).replace("+", "%20");
        String label = encodedIssuer + ":" + encodedEmail;

        return String.format(
                "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                label, secret, encodedIssuer
        );
    }

    /**
     * Generates the 6-digit TOTP code for a given secret and specific epoch second.
     */
    public String generateCode(String secret, long epochSecond) {
        long timeStep = epochSecond / TIME_STEP_SECONDS;
        return generateCodeForTimeStep(secret, timeStep);
    }

    /**
     * Generates the 6-digit TOTP code for a given secret and timeStep.
     */
    public String generateCodeForTimeStep(String secret, long timeStep) {
        byte[] keyBytes = base32.decode(secret.toUpperCase());
        byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data);

            // Dynamic truncation (RFC 4226 / RFC 6238)
            int offset = hash[hash.length - 1] & 0x0F;
            int binaryCode = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binaryCode % DIGIT_MODULO;
            return String.format("%06d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate TOTP code", e);
        }
    }

    /**
     * Verifies a submitted TOTP code against a secret and the user's last recorded time-step.
     * Prevents replay by ensuring the matched time-step is strictly greater than lastUsedTimeStep.
     *
     * @param secret Base32 secret
     * @param code submitted 6-digit code
     * @param lastUsedTimeStep last used time-step or null if none
     * @return Optional containing the matched time-step if valid and un-replayed, or empty if invalid
     */
    public Optional<Long> verifyCode(String secret, String code, Long lastUsedTimeStep) {
        if (code == null || code.trim().length() != DIGITS || secret == null) {
            return Optional.empty();
        }

        String normalizedCode = code.trim();
        long currentEpochSecond = Instant.now().getEpochSecond();
        long currentStep = currentEpochSecond / TIME_STEP_SECONDS;

        // Check window: -1, 0, +1
        for (long step = currentStep - WINDOW_STEPS; step <= currentStep + WINDOW_STEPS; step++) {
            // Replay protection: cannot reuse a time step that has already been verified
            if (lastUsedTimeStep != null && step <= lastUsedTimeStep) {
                continue;
            }

            String expectedCode = generateCodeForTimeStep(secret, step);
            if (constantTimeEquals(normalizedCode, expectedCode)) {
                return Optional.of(step);
            }
        }

        return Optional.empty();
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
