package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.dto.AuthResponse;
import com.ayshriv.authplatform.dto.LoginResult;
import com.ayshriv.authplatform.dto.UserResponse;
import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.LogSubAction;
import com.ayshriv.authplatform.entity.RefreshToken;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.repository.RefreshTokenRepository;
import com.ayshriv.authplatform.security.JwtTokenService;
import com.ayshriv.authplatform.util.TokenHashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthenticationResultService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationResultService.class);

    private final JwtTokenService jwtTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final AppProperties appProperties;

    private final MfaTokenService mfaTokenService;

    public AuthenticationResultService(JwtTokenService jwtTokenService,
                                       RefreshTokenRepository refreshTokenRepository,
                                       AuditService auditService,
                                       AppProperties appProperties,
                                       MfaTokenService mfaTokenService) {
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
        this.appProperties = appProperties;
        this.mfaTokenService = mfaTokenService;
    }

    /**
     * Processes authentication for a user given their first factor method.
     * If MFA is enabled, generates a short-lived mfaToken and returns an MFA challenge with available factors.
     * If MFA is not enabled, directly issues platform tokens.
     */
    @Transactional
    public LoginResult processAuthentication(User user, String initialFactor, LogAction logAction, String ipAddress, String userAgent) {
        if (user.isMfaEnabled()) {
            java.util.List<String> availableFactors = new java.util.ArrayList<>();
            if (user.getTotpSecretEncrypted() != null) {
                availableFactors.add("TOTP");
            }
            if (user.isEmailVerified()) {
                availableFactors.add("EMAIL");
            }
            if (user.isPhoneVerified() && user.getPhoneE164() != null) {
                availableFactors.add("SMS");
            }
            availableFactors.add("RECOVERY_CODE");

            String mfaToken = mfaTokenService.createMfaToken(user.getId(), initialFactor);
            auditService.recordActivity(user.getId(), LogAction.MFA_SETUP, LogSubAction.CHALLENGE_ISSUED);
            log.info("MFA challenge issued for user id={}, availableFactors={}", user.getId(), availableFactors);

            return LoginResult.mfaChallenge(mfaToken, availableFactors);
        }

        AuthResponse authResponse = issueTokensWithClaims(
                user,
                java.util.List.of(initialFactor),
                Instant.now(),
                logAction,
                ipAddress,
                userAgent
        );
        return LoginResult.tokens(authResponse);
    }

    /**
     * Issues access token and refresh token for an authenticated user with explicit amr and auth_time.
     */
    @Transactional
    public AuthResponse issueTokensWithClaims(User user,
                                              java.util.List<String> amr,
                                              Instant authTime,
                                              LogAction logAction,
                                              String ipAddress,
                                              String userAgent) {
        String accessToken = jwtTokenService.generateAccessToken(user, amr, authTime);
        String rawRefreshToken = TokenHashUtils.generateSecureToken();
        String refreshTokenHash = TokenHashUtils.hashToken(rawRefreshToken);
        String familyId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(appProperties.security().jwt().refreshTokenExpiryDays()));

        RefreshToken refreshToken = new RefreshToken(user, refreshTokenHash, familyId, expiresAt, ipAddress, userAgent);
        refreshTokenRepository.save(refreshToken);

        auditService.recordActivity(user.getId(), logAction, LogSubAction.SUCCESS);
        log.info("Authentication tokens successfully issued for user id={}, action={}, amr={}", user.getId(), logAction, amr);

        return AuthResponse.of(
                accessToken,
                rawRefreshToken,
                jwtTokenService.getAccessTokenExpirySeconds(),
                UserResponse.from(user)
        );
    }

    /**
     * Legacy issueTokens method retaining backward compatibility.
     */
    @Transactional
    public AuthResponse issueTokens(User user, LogAction logAction, String ipAddress, String userAgent) {
        return issueTokensWithClaims(user, java.util.List.of("pwd"), Instant.now(), logAction, ipAddress, userAgent);
    }
}

