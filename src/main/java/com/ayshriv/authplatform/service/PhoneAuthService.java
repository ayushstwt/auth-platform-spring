package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.dto.AuthResponse;
import com.ayshriv.authplatform.dto.PhoneLoginConfirmRequest;
import com.ayshriv.authplatform.dto.PhoneLoginSendRequest;
import com.ayshriv.authplatform.dto.PhoneOtpSendRequest;
import com.ayshriv.authplatform.dto.PhoneVerifyRequest;
import com.ayshriv.authplatform.dto.UserResponse;
import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.LogSubAction;
import com.ayshriv.authplatform.entity.OtpChallenge;
import com.ayshriv.authplatform.entity.OtpChannel;
import com.ayshriv.authplatform.entity.OtpPurpose;
import com.ayshriv.authplatform.entity.RefreshToken;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.exception.BadRequestException;
import com.ayshriv.authplatform.exception.ResourceAlreadyExistException;
import com.ayshriv.authplatform.exception.ResourceNotFoundException;
import com.ayshriv.authplatform.exception.UnauthorizedException;
import com.ayshriv.authplatform.repository.RefreshTokenRepository;
import com.ayshriv.authplatform.repository.UserRepository;
import com.ayshriv.authplatform.security.JwtTokenService;
import com.ayshriv.authplatform.util.LogMaskingUtils;
import com.ayshriv.authplatform.util.OtpCryptoUtils;
import com.ayshriv.authplatform.util.PhoneNumberValidationUtils;
import com.ayshriv.authplatform.util.TokenHashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PhoneAuthService {

    private static final Logger log = LoggerFactory.getLogger(PhoneAuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final SmsSender smsSender;
    private final SmsAbuseProtectionService smsAbuseProtectionService;
    private final JwtTokenService jwtTokenService;
    private final AuditService auditService;
    private final AppProperties appProperties;

    public PhoneAuthService(UserRepository userRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            OtpService otpService,
                            SmsSender smsSender,
                            SmsAbuseProtectionService smsAbuseProtectionService,
                            JwtTokenService jwtTokenService,
                            AuditService auditService,
                            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpService = otpService;
        this.smsSender = smsSender;
        this.smsAbuseProtectionService = smsAbuseProtectionService;
        this.jwtTokenService = jwtTokenService;
        this.auditService = auditService;
        this.appProperties = appProperties;
    }

    @Transactional
    public void sendPhoneVerificationOtp(Long userId, PhoneOtpSendRequest request, String requestIp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        String normalizedPhone = PhoneNumberValidationUtils.normalizeToE164(
                request.phoneNumber(),
                appProperties.sms().defaultRegion()
        );

        // Check if phone number is already verified on another account
        Optional<User> existingUser = userRepository.findByPhoneE164(normalizedPhone);
        if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
            log.warn("Phone number {} already registered by another account", LogMaskingUtils.maskPhone(normalizedPhone));
            throw new ResourceAlreadyExistException(ErrorMessages.E003, "This phone number is already registered to another user");
        }

        // Re-authentication check: if user already has a verified phone number, requires recent login
        if (user.isPhoneVerified() && user.getPhoneE164() != null) {
            long maxAgeMinutes = appProperties.security().auth().reauthValidityMinutes();
            Instant threshold = Instant.now().minus(Duration.ofMinutes(maxAgeMinutes));
            if (user.getLastLoginAt() == null || user.getLastLoginAt().isBefore(threshold)) {
                log.warn("Re-authentication required for user id={} to change verified phone", userId);
                throw new UnauthorizedException(ErrorMessages.E020);
            }
        }

        // Abuse & fraud check
        smsAbuseProtectionService.validateSmsSendAllowed(normalizedPhone, userId, requestIp);

        // Generate and record challenge
        String code = otpService.generateAndSaveOtp(
                user,
                OtpChannel.SMS,
                OtpPurpose.PHONE_VERIFY,
                normalizedPhone,
                requestIp
        );

        smsSender.sendOtpSms(normalizedPhone, code);
        smsAbuseProtectionService.recordSmsSent();

        LogAction logAction = user.isPhoneVerified() ? LogAction.PHONE_CHANGE : LogAction.PHONE_ADD;
        auditService.recordActivity(userId, logAction, LogSubAction.CHALLENGE_ISSUED);
        log.info("Sent phone verification OTP to user id={}, phone={}", userId, LogMaskingUtils.maskPhone(normalizedPhone));
    }

    @Transactional
    public UserResponse verifyUserPhone(Long userId, PhoneVerifyRequest request, String requestIp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        String normalizedPhone = PhoneNumberValidationUtils.normalizeToE164(
                request.phoneNumber(),
                appProperties.sms().defaultRegion()
        );

        // Verify challenge
        otpService.verifyOtp(normalizedPhone, OtpPurpose.PHONE_VERIFY, request.code(), requestIp);

        // Update phone
        user.setPhoneE164(normalizedPhone);
        user.setPhoneVerified(true);
        User saved = userRepository.save(user);

        auditService.recordActivity(userId, LogAction.PHONE_VERIFY, LogSubAction.SUCCESS);
        log.info("Verified phone for user id={}, phone={}", userId, LogMaskingUtils.maskPhone(normalizedPhone));

        return UserResponse.from(saved);
    }

    @Transactional
    public void sendLoginPhoneOtp(PhoneLoginSendRequest request, String requestIp) {
        String normalizedPhone;
        try {
            normalizedPhone = PhoneNumberValidationUtils.normalizeToE164(
                    request.phoneNumber(),
                    appProperties.sms().defaultRegion()
            );
        } catch (BadRequestException e) {
            // Anti-enumeration dummy calculation
            OtpCryptoUtils.hashOtp("123456", "+919999999999", appProperties.otp().pepper());
            log.info("Invalid phone input format for login OTP: {}", LogMaskingUtils.maskPhone(request.phoneNumber()));
            return;
        }

        Optional<User> userOpt = userRepository.findByPhoneE164(normalizedPhone);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.isAccountActive() && user.isPhoneVerified()) {
                smsAbuseProtectionService.validateSmsSendAllowed(normalizedPhone, user.getId(), requestIp);

                String code = otpService.generateAndSaveOtp(
                        user,
                        OtpChannel.SMS,
                        OtpPurpose.LOGIN,
                        normalizedPhone,
                        requestIp
                );
                smsSender.sendOtpSms(normalizedPhone, code);
                smsAbuseProtectionService.recordSmsSent();

                log.info("Sent phone login OTP to user id={}, phone={}", user.getId(), LogMaskingUtils.maskPhone(normalizedPhone));
                return;
            } else {
                log.warn("Phone login OTP requested for locked/inactive or unverified user id={}", user.getId());
                auditService.recordActivity(user.getId(), LogAction.LOGIN, LogSubAction.BLOCKED);
            }
        } else {
            // Anti-enumeration dummy calculation
            OtpCryptoUtils.hashOtp("123456", normalizedPhone, appProperties.otp().pepper());
            log.info("Anti-enumeration: Phone login OTP requested for non-existent phone {}", LogMaskingUtils.maskPhone(normalizedPhone));
        }
    }

    @Transactional
    public AuthResponse confirmPhoneLoginOtp(PhoneLoginConfirmRequest request, String requestIp, String userAgent) {
        String normalizedPhone = PhoneNumberValidationUtils.normalizeToE164(
                request.phoneNumber(),
                appProperties.sms().defaultRegion()
        );

        OtpChallenge challenge = otpService.verifyOtp(normalizedPhone, OtpPurpose.LOGIN, request.code(), requestIp);
        User user = challenge.getUser();

        if (user == null) {
            user = userRepository.findByPhoneE164(normalizedPhone)
                    .orElseThrow(() -> new UnauthorizedException(ErrorMessages.E012));
        }

        if (!user.isAccountActive() || !user.isPhoneVerified()) {
            auditService.recordActivity(user.getId(), LogAction.LOGIN, LogSubAction.BLOCKED);
            throw new UnauthorizedException(ErrorMessages.E012);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtTokenService.generateAccessToken(user);
        String rawRefreshToken = TokenHashUtils.generateSecureToken();
        String refreshTokenHash = TokenHashUtils.hashToken(rawRefreshToken);
        String familyId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(appProperties.security().jwt().refreshTokenExpiryDays()));

        RefreshToken refreshToken = new RefreshToken(user, refreshTokenHash, familyId, expiresAt, requestIp, userAgent);
        refreshTokenRepository.save(refreshToken);

        auditService.recordActivity(user.getId(), LogAction.LOGIN, LogSubAction.SUCCESS);
        log.info("Phone OTP Login successful for user id={}", user.getId());

        return AuthResponse.of(
                accessToken,
                rawRefreshToken,
                jwtTokenService.getAccessTokenExpirySeconds(),
                UserResponse.from(user)
        );
    }
}
