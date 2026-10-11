package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.dto.AuthResponse;
import com.ayshriv.authplatform.dto.FederatedIdentityResponse;
import com.ayshriv.authplatform.entity.FederatedIdentity;
import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.LogSubAction;
import com.ayshriv.authplatform.entity.Role;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.entity.UserStatus;
import com.ayshriv.authplatform.exception.BadRequestException;
import com.ayshriv.authplatform.exception.ForbiddenException;
import com.ayshriv.authplatform.exception.ResourceAlreadyExistException;
import com.ayshriv.authplatform.exception.ResourceNotFoundException;
import com.ayshriv.authplatform.exception.UnauthorizedException;
import com.ayshriv.authplatform.repository.FederatedIdentityRepository;
import com.ayshriv.authplatform.repository.RoleRepository;
import com.ayshriv.authplatform.repository.UserRepository;
import com.ayshriv.authplatform.security.RateLimiter;
import com.ayshriv.authplatform.util.LogMaskingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);
    public static final String PROVIDER_GOOGLE = "GOOGLE";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FederatedIdentityRepository federatedIdentityRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AuthenticationResultService authenticationResultService;
    private final EmailSender emailSender;
    private final AuditService auditService;
    private final RateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final OAuthExchangeCodeService oAuthExchangeCodeService;

    public GoogleAuthService(UserRepository userRepository,
                             RoleRepository roleRepository,
                             FederatedIdentityRepository federatedIdentityRepository,
                             GoogleTokenVerifier googleTokenVerifier,
                             AuthenticationResultService authenticationResultService,
                             EmailSender emailSender,
                             AuditService auditService,
                             RateLimiter rateLimiter,
                             PasswordEncoder passwordEncoder,
                             OAuthExchangeCodeService oAuthExchangeCodeService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.federatedIdentityRepository = federatedIdentityRepository;
        this.googleTokenVerifier = googleTokenVerifier;
        this.authenticationResultService = authenticationResultService;
        this.emailSender = emailSender;
        this.auditService = auditService;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.oAuthExchangeCodeService = oAuthExchangeCodeService;
    }

    @Transactional
    public AuthResponse loginWithIdToken(String idToken, String ipAddress, String userAgent) {
        enforceRateLimit(ipAddress);

        GoogleIdTokenPayload payload = googleTokenVerifier.verify(idToken);
        User user = processGooglePayload(payload);

        return authenticationResultService.issueTokens(user, LogAction.GOOGLE_LOGIN, ipAddress, userAgent);
    }

    @Transactional
    public AuthResponse exchangeCode(String exchangeCode, String ipAddress, String userAgent) {
        enforceRateLimit(ipAddress);

        Long userId = oAuthExchangeCodeService.consumeCode(exchangeCode);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException(ErrorMessages.E027));

        if (!user.isAccountActive()) {
            auditService.recordActivity(user.getId(), LogAction.GOOGLE_LOGIN, LogSubAction.BLOCKED);
            throw new UnauthorizedException(ErrorMessages.E012);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return authenticationResultService.issueTokens(user, LogAction.GOOGLE_LOGIN, ipAddress, userAgent);
    }

    @Transactional
    public User processGooglePayload(GoogleIdTokenPayload payload) {
        // Scenario 1: Existing federated identity for this Google sub
        Optional<FederatedIdentity> existingIdentity = federatedIdentityRepository
                .findByProviderAndProviderSubject(PROVIDER_GOOGLE, payload.sub());

        if (existingIdentity.isPresent()) {
            User user = existingIdentity.get().getUser();
            if (!user.isAccountActive()) {
                auditService.recordActivity(user.getId(), LogAction.GOOGLE_LOGIN, LogSubAction.BLOCKED);
                throw new UnauthorizedException(ErrorMessages.E012);
            }
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
            log.info("Google login successful for user id={} via existing federated identity", user.getId());
            return user;
        }

        // Check if a local user exists with the same email
        Optional<User> localUserOpt = userRepository.findByEmail(payload.email());

        if (localUserOpt.isPresent()) {
            User localUser = localUserOpt.get();

            // Scenario 3: Local account exists but is NOT email-verified -> Prevent pre-account takeover!
            if (!localUser.isEmailVerified()) {
                log.warn("Blocked Google login/link for unverified user email: {}", LogMaskingUtils.maskEmail(payload.email()));
                auditService.recordActivity(localUser.getId(), LogAction.GOOGLE_LOGIN, LogSubAction.BLOCKED);
                throw new ForbiddenException(ErrorMessages.E023);
            }

            // Scenario 2: Local user exists with verified email & Google email_verified=true -> Link automatically & notify
            if (!localUser.isAccountActive()) {
                auditService.recordActivity(localUser.getId(), LogAction.GOOGLE_LOGIN, LogSubAction.BLOCKED);
                throw new UnauthorizedException(ErrorMessages.E012);
            }

            FederatedIdentity identity = new FederatedIdentity(localUser, PROVIDER_GOOGLE, payload.sub(), payload.email());
            federatedIdentityRepository.save(identity);

            localUser.setLastLoginAt(Instant.now());
            userRepository.save(localUser);

            auditService.recordActivity(localUser.getId(), LogAction.GOOGLE_LINKED, LogSubAction.SUCCESS);
            log.info("Auto-linked Google identity to existing verified user id={}", localUser.getId());

            // Notify user by email about auto-linking
            try {
                emailSender.sendAccountLinkedEmail(localUser.getEmail(), localUser.getFirstName(), "Google");
            } catch (Exception e) {
                log.error("Failed to send auto-link notification email to {}: {}",
                        LogMaskingUtils.maskEmail(localUser.getEmail()), e.getMessage());
            }

            return localUser;
        }

        // Scenario 4: No user exists -> Create new user with emailVerified=true, hasPassword=false
        User newUser = new User();
        newUser.setEmail(payload.email());
        // Unusable random bcrypt hash
        newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        newUser.setHasPassword(false);
        newUser.setFirstName(payload.givenName() != null && !payload.givenName().isBlank() ? payload.givenName().trim() : "Google");
        newUser.setLastName(payload.familyName() != null && !payload.familyName().isBlank() ? payload.familyName().trim() : "User");
        newUser.setEmailVerified(true);
        newUser.setStatus(UserStatus.ACTIVE);
        newUser.setLastLoginAt(Instant.now());

        Role userRole = roleRepository.findByName(Role.ROLE_USER)
                .orElseGet(() -> roleRepository.save(new Role(Role.ROLE_USER)));
        newUser.setRoles(Set.of(userRole));

        User savedUser = userRepository.save(newUser);

        FederatedIdentity identity = new FederatedIdentity(savedUser, PROVIDER_GOOGLE, payload.sub(), payload.email());
        federatedIdentityRepository.save(identity);

        auditService.recordActivity(savedUser.getId(), LogAction.REGISTER, LogSubAction.SUCCESS);
        auditService.recordActivity(savedUser.getId(), LogAction.GOOGLE_LINKED, LogSubAction.SUCCESS);
        log.info("Created new user id={} and linked Google identity", savedUser.getId());

        return savedUser;
    }

    @Transactional
    public FederatedIdentityResponse linkGoogleAccount(Long userId, String idToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        GoogleIdTokenPayload payload = googleTokenVerifier.verify(idToken);

        // Check if this Google sub is already linked to another account
        Optional<FederatedIdentity> existingBySub = federatedIdentityRepository
                .findByProviderAndProviderSubject(PROVIDER_GOOGLE, payload.sub());
        if (existingBySub.isPresent()) {
            if (existingBySub.get().getUser().getId().equals(userId)) {
                return FederatedIdentityResponse.from(existingBySub.get());
            }
            throw new ResourceAlreadyExistException(ErrorMessages.E025);
        }

        // Check if user already linked Google
        if (federatedIdentityRepository.existsByUserIdAndProvider(userId, PROVIDER_GOOGLE)) {
            throw new ResourceAlreadyExistException(ErrorMessages.E025, "Google account is already linked to your profile");
        }

        FederatedIdentity identity = new FederatedIdentity(user, PROVIDER_GOOGLE, payload.sub(), payload.email());
        FederatedIdentity saved = federatedIdentityRepository.save(identity);

        auditService.recordActivity(userId, LogAction.GOOGLE_LINKED, LogSubAction.SUCCESS);
        log.info("User id={} explicitly linked Google account", userId);

        return FederatedIdentityResponse.from(saved);
    }

    @Transactional
    public void unlinkGoogleAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        FederatedIdentity identity = federatedIdentityRepository
                .findByUserIdAndProvider(userId, PROVIDER_GOOGLE)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E001, "Google identity not linked"));

        long linkedIdentitiesCount = federatedIdentityRepository.countByUserId(userId);

        // Security requirement: Block unlinking the last remaining login method
        if (!user.isHasPassword() && linkedIdentitiesCount <= 1) {
            log.warn("User id={} attempted to unlink Google without password or alternative identity", userId);
            throw new BadRequestException(ErrorMessages.E024);
        }

        federatedIdentityRepository.delete(identity);
        auditService.recordActivity(userId, LogAction.GOOGLE_UNLINKED, LogSubAction.SUCCESS);
        log.info("User id={} successfully unlinked Google identity", userId);
    }

    @Transactional(readOnly = true)
    public List<FederatedIdentityResponse> getIdentities(Long userId) {
        return federatedIdentityRepository.findByUserId(userId).stream()
                .map(FederatedIdentityResponse::from)
                .toList();
    }

    private void enforceRateLimit(String ipAddress) {
        if (ipAddress != null && !rateLimiter.tryAcquire("ip:" + ipAddress)) {
            throw new ForbiddenException(ErrorMessages.E016);
        }
    }
}
