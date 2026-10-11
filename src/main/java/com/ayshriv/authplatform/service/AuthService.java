package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.dto.AuthResponse;
import com.ayshriv.authplatform.dto.LoginRequest;
import com.ayshriv.authplatform.dto.LoginResult;
import com.ayshriv.authplatform.dto.RefreshTokenRequest;
import com.ayshriv.authplatform.dto.RegisterRequest;
import com.ayshriv.authplatform.dto.UserResponse;
import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.LogSubAction;
import com.ayshriv.authplatform.entity.RefreshToken;
import com.ayshriv.authplatform.entity.Role;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.entity.UserStatus;
import com.ayshriv.authplatform.exception.ForbiddenException;
import com.ayshriv.authplatform.exception.ResourceAlreadyExistException;
import com.ayshriv.authplatform.exception.UnauthorizedException;
import com.ayshriv.authplatform.repository.RefreshTokenRepository;
import com.ayshriv.authplatform.repository.RoleRepository;
import com.ayshriv.authplatform.repository.UserRepository;
import com.ayshriv.authplatform.security.JwtTokenService;
import com.ayshriv.authplatform.security.PasswordPolicyValidator;
import com.ayshriv.authplatform.util.LogMaskingUtils;
import com.ayshriv.authplatform.util.TokenHashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String DUMMY_BCRYPT_HASH = "$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final JwtTokenService jwtTokenService;
    private final AuditService auditService;
    private final UserLockoutService userLockoutService;
    private final TokenRevocationService tokenRevocationService;
    private final AuthenticationResultService authenticationResultService;
    private final AppProperties appProperties;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicyValidator passwordPolicyValidator,
                       JwtTokenService jwtTokenService,
                       AuditService auditService,
                       UserLockoutService userLockoutService,
                       TokenRevocationService tokenRevocationService,
                       AuthenticationResultService authenticationResultService,
                       AppProperties appProperties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.jwtTokenService = jwtTokenService;
        this.auditService = auditService;
        this.userLockoutService = userLockoutService;
        this.tokenRevocationService = tokenRevocationService;
        this.authenticationResultService = authenticationResultService;
        this.appProperties = appProperties;
    }


    @Transactional
    public UserResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        String normalizedEmail = request.email().trim().toLowerCase();
        passwordPolicyValidator.validate(request.password());

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Registration attempt with duplicate email: {}", LogMaskingUtils.maskEmail(normalizedEmail));
            throw new ResourceAlreadyExistException(ErrorMessages.E003, "Account already exists with this email address");
        }

        Role userRole = roleRepository.findByName(Role.ROLE_USER)
                .orElseGet(() -> roleRepository.save(new Role(Role.ROLE_USER)));

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);
        user.setRoles(Set.of(userRole));

        User saved = userRepository.save(user);
        auditService.recordActivity(saved.getId(), LogAction.REGISTER, LogSubAction.SUCCESS);

        log.info("User registered successfully: id={}, email={}", saved.getId(), LogMaskingUtils.maskEmail(saved.getEmail()));
        return UserResponse.from(saved);
    }

    @Transactional
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
        String normalizedEmail = request.email().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        if (userOpt.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            log.warn("Login failure: unknown user {}", LogMaskingUtils.maskEmail(normalizedEmail));
            auditService.recordActivity(null, LogAction.LOGIN, LogSubAction.FAILURE);
            throw new UnauthorizedException(ErrorMessages.E012);
        }

        User user = userOpt.get();

        if (!user.isAccountActive()) {
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            log.warn("Login failure: account locked/inactive for user id={}", user.getId());
            auditService.recordActivity(user.getId(), LogAction.LOGIN, LogSubAction.BLOCKED);
            throw new UnauthorizedException(ErrorMessages.E012);
        }

        if (appProperties.security().auth().requireVerifiedEmailBeforeLogin() && !user.isEmailVerified()) {
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            log.warn("Login failure: unverified email for user id={}", user.getId());
            auditService.recordActivity(user.getId(), LogAction.LOGIN, LogSubAction.BLOCKED);
            throw new ForbiddenException(ErrorMessages.E014);
        }

        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());

        if (!passwordMatches) {
            userLockoutService.recordFailedLogin(user.getId());
            throw new UnauthorizedException(ErrorMessages.E012);
        }

        userLockoutService.resetFailedLogins(user.getId());
        handleSuccessfulLogin(user, request.password());

        return authenticationResultService.processAuthentication(user, "pwd", LogAction.LOGIN, ipAddress, userAgent);
    }


    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, String ipAddress, String userAgent) {
        String tokenHash = TokenHashUtils.hashToken(request.refreshToken());
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            log.warn("Refresh attempt with non-existent token");
            throw new UnauthorizedException(ErrorMessages.E013);
        }

        RefreshToken token = tokenOpt.get();

        if (token.isRevoked()) {
            log.warn("TOKEN REUSE DETECTED! Family {} revoked for user id={}", token.getFamilyId(), token.getUser().getId());
            tokenRevocationService.handleTokenReuse(token.getUser().getId(), token.getFamilyId());
            throw new UnauthorizedException(ErrorMessages.E013);
        }

        if (token.isExpired()) {
            log.warn("Refresh attempt with expired token id={}", token.getId());
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
            throw new UnauthorizedException(ErrorMessages.E013);
        }

        User user = token.getUser();
        if (!user.isAccountActive()) {
            throw new UnauthorizedException(ErrorMessages.E013);
        }

        String newRawToken = TokenHashUtils.generateSecureToken();
        String newHash = TokenHashUtils.hashToken(newRawToken);

        token.setRevokedAt(Instant.now());
        token.setReplacedBy(newHash);
        refreshTokenRepository.save(token);

        Instant expiresAt = Instant.now().plus(Duration.ofDays(appProperties.security().jwt().refreshTokenExpiryDays()));
        RefreshToken newRefreshToken = new RefreshToken(user, newHash, token.getFamilyId(), expiresAt, ipAddress, userAgent);
        refreshTokenRepository.save(newRefreshToken);

        String newAccessToken = jwtTokenService.generateAccessToken(user);
        auditService.recordActivity(user.getId(), LogAction.TOKEN_REFRESH, LogSubAction.SUCCESS);

        return AuthResponse.of(
                newAccessToken,
                newRawToken,
                jwtTokenService.getAccessTokenExpirySeconds(),
                UserResponse.from(user)
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = TokenHashUtils.hashToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                refreshTokenRepository.revokeByFamilyId(token.getFamilyId(), Instant.now());
                auditService.recordActivity(token.getUser().getId(), LogAction.LOGOUT, LogSubAction.SUCCESS);
                log.info("Logged out token family {} for user id={}", token.getFamilyId(), token.getUser().getId());
            });
        }
    }

    @Transactional
    public void logoutAll(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
        auditService.recordActivity(userId, LogAction.LOGOUT, LogSubAction.REVOKED);
        log.info("Revoked all sessions for user id={}", userId);
    }

    private void handleSuccessfulLogin(User user, String rawPassword) {
        user.setLastLoginAt(Instant.now());

        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            log.info("Upgraded password encoding for user id={}", user.getId());
        }

        userRepository.save(user);
    }
}
