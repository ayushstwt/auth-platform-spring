package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.exception.UnauthorizedException;
import com.ayshriv.authplatform.security.RsaKeyProvider;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for issuing and validating short-lived MFA intermediate tokens.
 * MFA tokens are signed JWTs with:
 * - audience = "auth-platform-mfa" (distinct from general API audience)
 * - type = "mfa_token"
 * - subject = user ID
 * - claim "first_factor" = the initial authentication factor used (e.g. "pwd", "email_otp", "phone_otp", "google")
 * - short TTL (configurable, default 5 mins)
 * Also tracks failed attempts per mfaToken to enforce brute force protection.
 */
@Service
public class MfaTokenService {

    public static final String MFA_AUDIENCE = "auth-platform-mfa";
    public static final String TOKEN_TYPE = "mfa_token";

    private final RsaKeyProvider rsaKeyProvider;
    private final AppProperties appProperties;

    // Track failed verification attempts per tokenId (jti)
    private final Map<String, AtomicInteger> failedAttemptsMap = new ConcurrentHashMap<>();

    public MfaTokenService(RsaKeyProvider rsaKeyProvider, AppProperties appProperties) {
        this.rsaKeyProvider = rsaKeyProvider;
        this.appProperties = appProperties;
    }

    public record MfaTokenPayload(Long userId, String initialFactor, String tokenId) {}

    /**
     * Issues a short-lived MFA challenge JWT token.
     */
    public String createMfaToken(Long userId, String initialFactor) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(appProperties.mfa().tokenTtlMinutes()));
        String jti = UUID.randomUUID().toString();

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .issuer(appProperties.security().jwt().issuer())
                .audience(MFA_AUDIENCE)
                .claim("token_type", TOKEN_TYPE)
                .claim("first_factor", initialFactor)
                .jwtID(jti)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiry))
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(rsaKeyProvider.getKeyId())
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claimsSet);

        try {
            RSASSASigner signer = new RSASSASigner(rsaKeyProvider.getPrivateKey());
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign MFA intermediate token", e);
        }
    }

    /**
     * Validates an MFA token string, verifies signature, expiration, audience, and type.
     */
    public MfaTokenPayload validateMfaToken(String mfaToken) {
        if (mfaToken == null || mfaToken.isBlank()) {
            throw new UnauthorizedException(ErrorMessages.E028);
        }

        try {
            SignedJWT signedJWT = SignedJWT.parse(mfaToken);
            RSASSAVerifier verifier = new RSASSAVerifier(rsaKeyProvider.getPublicKey());
            if (!signedJWT.verify(verifier)) {
                throw new UnauthorizedException(ErrorMessages.E028);
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Check audience
            if (!claims.getAudience().contains(MFA_AUDIENCE)) {
                throw new UnauthorizedException(ErrorMessages.E028);
            }

            // Check token type
            if (!TOKEN_TYPE.equals(claims.getStringClaim("token_type"))) {
                throw new UnauthorizedException(ErrorMessages.E028);
            }

            // Check expiration
            Date expirationTime = claims.getExpirationTime();
            if (expirationTime == null || expirationTime.before(new Date())) {
                throw new UnauthorizedException(ErrorMessages.E028);
            }

            String jti = claims.getJWTID();
            int maxAttempts = appProperties.mfa().maxAttempts();
            AtomicInteger attempts = failedAttemptsMap.get(jti);
            if (attempts != null && attempts.get() >= maxAttempts) {
                throw new UnauthorizedException(ErrorMessages.E028, "MFA token locked due to maximum failed attempts reached");
            }

            Long userId = Long.parseLong(claims.getSubject());
            String initialFactor = claims.getStringClaim("first_factor");

            return new MfaTokenPayload(userId, initialFactor, jti);
        } catch (UnauthorizedException ue) {
            throw ue;
        } catch (Exception e) {
            throw new UnauthorizedException(ErrorMessages.E028);
        }
    }

    /**
     * Records a failed MFA attempt for a token ID.
     */
    public void recordFailedAttempt(String tokenId) {
        if (tokenId == null) return;
        failedAttemptsMap.computeIfAbsent(tokenId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Clears failed attempt tracking upon successful token consumption.
     */
    public void clearToken(String tokenId) {
        if (tokenId != null) {
            failedAttemptsMap.remove(tokenId);
        }
    }
}
