package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.dto.AuthResponse;
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

    public AuthenticationResultService(JwtTokenService jwtTokenService,
                                       RefreshTokenRepository refreshTokenRepository,
                                       AuditService auditService,
                                       AppProperties appProperties) {
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
        this.appProperties = appProperties;
    }

    /**
     * Issues access token and refresh token for an authenticated user.
     * In Phase 6, this service will check if MFA is required and return an MFA challenge instead if applicable.
     */
    @Transactional
    public AuthResponse issueTokens(User user, LogAction logAction, String ipAddress, String userAgent) {
        String accessToken = jwtTokenService.generateAccessToken(user);
        String rawRefreshToken = TokenHashUtils.generateSecureToken();
        String refreshTokenHash = TokenHashUtils.hashToken(rawRefreshToken);
        String familyId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(appProperties.security().jwt().refreshTokenExpiryDays()));

        RefreshToken refreshToken = new RefreshToken(user, refreshTokenHash, familyId, expiresAt, ipAddress, userAgent);
        refreshTokenRepository.save(refreshToken);

        auditService.recordActivity(user.getId(), logAction, LogSubAction.SUCCESS);
        log.info("Authentication tokens successfully issued for user id={}, action={}", user.getId(), logAction);

        return AuthResponse.of(
                accessToken,
                rawRefreshToken,
                jwtTokenService.getAccessTokenExpirySeconds(),
                UserResponse.from(user)
        );
    }
}
