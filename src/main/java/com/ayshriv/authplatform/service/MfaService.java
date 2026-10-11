package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.dto.AuthResponse;
import com.ayshriv.authplatform.dto.DisableMfaRequest;
import com.ayshriv.authplatform.dto.LoginResult;
import com.ayshriv.authplatform.dto.MfaChallengeSendRequest;
import com.ayshriv.authplatform.dto.MfaVerifyRequest;
import com.ayshriv.authplatform.dto.RecoveryCodesResponse;
import com.ayshriv.authplatform.dto.TotpConfirmRequest;
import com.ayshriv.authplatform.dto.TotpConfirmResponse;
import com.ayshriv.authplatform.dto.TotpSetupResponse;
import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.LogSubAction;
import com.ayshriv.authplatform.entity.OtpChannel;
import com.ayshriv.authplatform.entity.OtpPurpose;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.exception.BadRequestException;
import com.ayshriv.authplatform.exception.ForbiddenException;
import com.ayshriv.authplatform.exception.ResourceNotFoundException;
import com.ayshriv.authplatform.exception.UnauthorizedException;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MfaService {

    private static final Logger log = LoggerFactory.getLogger(MfaService.class);

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final AesGcmEncryptionService aesGcmEncryptionService;
    private final RecoveryCodeService recoveryCodeService;
    private final MfaTokenService mfaTokenService;
    private final OtpService otpService;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final SmsAbuseProtectionService smsAbuseProtectionService;
    private final AuthenticationResultService authenticationResultService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final AppProperties appProperties;

    // Transient in-memory store for pending TOTP setup secrets before confirmation: userId -> pendingSecret
    private final Map<Long, String> pendingTotpSecrets = new ConcurrentHashMap<>();

    public MfaService(UserRepository userRepository,
                      TotpService totpService,
                      AesGcmEncryptionService aesGcmEncryptionService,
                      RecoveryCodeService recoveryCodeService,
                      MfaTokenService mfaTokenService,
                      OtpService otpService,
                      EmailSender emailSender,
                      SmsSender smsSender,
                      SmsAbuseProtectionService smsAbuseProtectionService,
                      AuthenticationResultService authenticationResultService,
                      AuditService auditService,
                      PasswordEncoder passwordEncoder,
                      RateLimiter rateLimiter,
                      AppProperties appProperties) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.aesGcmEncryptionService = aesGcmEncryptionService;
        this.recoveryCodeService = recoveryCodeService;
        this.mfaTokenService = mfaTokenService;
        this.otpService = otpService;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.smsAbuseProtectionService = smsAbuseProtectionService;
        this.authenticationResultService = authenticationResultService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.appProperties = appProperties;
    }

    /**
     * Step 1 of TOTP setup: generates a new TOTP secret and builds the otpauth URI.
     * Keeps the secret pending until confirmed.
     */
    public TotpSetupResponse setupTotp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        if (user.isMfaEnabled()) {
            throw new BadRequestException(ErrorMessages.E032);
        }

        String plainSecret = totpService.generateSecret();
        pendingTotpSecrets.put(userId, plainSecret);

        String otpauthUri = totpService.buildOtpAuthUri(user.getEmail(), plainSecret);
        auditService.recordActivity(userId, LogAction.MFA_SETUP, LogSubAction.ATTEMPT);
        log.info("TOTP setup initiated for user id={}", userId);

        return new TotpSetupResponse(plainSecret, otpauthUri);
    }

    /**
     * Step 2 of TOTP setup: validates the submitted code against the pending secret,
     * encrypts and saves the secret, generates 10 recovery codes, marks MFA enabled,
     * sends an alert email, and audits the event.
     */
    @Transactional
    public TotpConfirmResponse confirmTotp(Long userId, TotpConfirmRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        if (user.isMfaEnabled()) {
            throw new BadRequestException(ErrorMessages.E032);
        }

        String pendingSecret = pendingTotpSecrets.get(userId);
        if (pendingSecret == null) {
            throw new BadRequestException("No pending TOTP setup found. Please initiate setup first.");
        }

        Optional<Long> timeStepOpt = totpService.verifyCode(pendingSecret, request.code(), null);
        if (timeStepOpt.isEmpty()) {
            auditService.recordActivity(userId, LogAction.MFA_SETUP, LogSubAction.FAILURE);
            throw new BadRequestException(ErrorMessages.E029);
        }

        // Encrypt secret with AES-256-GCM
        String encryptedSecret = aesGcmEncryptionService.encrypt(pendingSecret);
        user.setTotpSecretEncrypted(encryptedSecret);
        user.setTotpKeyId(aesGcmEncryptionService.getCurrentKeyId());
        user.setTotpLastTimestep(timeStepOpt.get());
        user.setMfaEnabled(true);
        userRepository.save(user);

        // Remove pending secret
        pendingTotpSecrets.remove(userId);

        // Generate 10 single-use recovery codes
        List<String> recoveryCodes = recoveryCodeService.generateAndSaveRecoveryCodes(user);

        auditService.recordActivity(userId, LogAction.MFA_ENABLE, LogSubAction.SUCCESS);
        log.info("MFA successfully enabled for user id={}", userId);

        try {
            emailSender.sendMfaEnabledEmail(user.getEmail(), user.getFirstName());
        } catch (Exception e) {
            log.error("Failed to send MFA enabled email alert to user id={}: {}", userId, e.getMessage());
        }

        return new TotpConfirmResponse(recoveryCodes);
    }

    /**
     * Disables MFA for the user. Requires confirmation by either password or current MFA code.
     */
    @Transactional
    public void disableMfa(Long userId, DisableMfaRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        if (!user.isMfaEnabled()) {
            throw new BadRequestException(ErrorMessages.E033);
        }

        boolean verified = false;

        // Try password verification if user has password and password is provided
        if (request.password() != null && !request.password().isBlank()) {
            if (user.isHasPassword() && passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                verified = true;
            }
        }

        // Try code verification if code is provided
        if (!verified && request.code() != null && !request.code().isBlank()) {
            if (user.getTotpSecretEncrypted() != null) {
                String plainSecret = aesGcmEncryptionService.decrypt(user.getTotpSecretEncrypted());
                Optional<Long> matched = totpService.verifyCode(plainSecret, request.code(), user.getTotpLastTimestep());
                if (matched.isPresent()) {
                    user.setTotpLastTimestep(matched.get());
                    verified = true;
                }
            }

            if (!verified) {
                verified = recoveryCodeService.validateAndConsumeRecoveryCode(user, request.code());
            }
        }

        if (!verified) {
            auditService.recordActivity(userId, LogAction.MFA_DISABLE, LogSubAction.FAILURE);
            throw new UnauthorizedException(ErrorMessages.E029, "Invalid password or verification code to disable MFA");
        }

        user.setMfaEnabled(false);
        user.setTotpSecretEncrypted(null);
        user.setTotpKeyId(null);
        user.setTotpLastTimestep(null);
        userRepository.save(user);

        recoveryCodeService.deleteAllForUser(userId);

        auditService.recordActivity(userId, LogAction.MFA_DISABLE, LogSubAction.SUCCESS);
        log.info("MFA disabled for user id={}", userId);

        try {
            emailSender.sendMfaDisabledEmail(user.getEmail(), user.getFirstName());
        } catch (Exception e) {
            log.error("Failed to send MFA disabled email alert to user id={}: {}", userId, e.getMessage());
        }
    }

    /**
     * Regenerates 10 single-use recovery codes for the user.
     */
    @Transactional
    public RecoveryCodesResponse regenerateRecoveryCodes(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        if (!user.isMfaEnabled()) {
            throw new BadRequestException(ErrorMessages.E033);
        }

        List<String> newCodes = recoveryCodeService.generateAndSaveRecoveryCodes(user);
        auditService.recordActivity(userId, LogAction.RECOVERY_CODES_REGENERATED, LogSubAction.SUCCESS);
        log.info("Recovery codes regenerated for user id={}", userId);

        try {
            emailSender.sendRecoveryCodesRegeneratedEmail(user.getEmail(), user.getFirstName());
        } catch (Exception e) {
            log.error("Failed to send recovery codes regenerated email to user id={}: {}", userId, e.getMessage());
        }

        return new RecoveryCodesResponse(newCodes);
    }

    /**
     * Sends fallback MFA OTP to user's verified EMAIL or SMS.
     */
    @Transactional
    public void sendMfaOtp(MfaChallengeSendRequest request, String requestIp) {
        var payload = mfaTokenService.validateMfaToken(request.mfaToken());
        User user = userRepository.findById(payload.userId())
                .orElseThrow(() -> new UnauthorizedException(ErrorMessages.E028));

        String channel = request.channel().toUpperCase().trim();

        if ("EMAIL".equals(channel)) {
            if (!user.isEmailVerified()) {
                throw new ForbiddenException(ErrorMessages.E030, "Email is not verified for MFA fallback");
            }
            String code = otpService.generateAndSaveOtp(user, OtpChannel.EMAIL, OtpPurpose.MFA, user.getEmail(), requestIp);
            emailSender.sendLoginOtpEmail(user.getEmail(), user.getFirstName(), code);
            log.info("Sent MFA fallback OTP via email to user id={}", user.getId());
        } else if ("SMS".equals(channel)) {
            if (!user.isPhoneVerified() || user.getPhoneE164() == null) {
                throw new ForbiddenException(ErrorMessages.E030, "Phone number is not verified for MFA fallback");
            }
            smsAbuseProtectionService.validateSmsSendAllowed(user.getPhoneE164(), user.getId(), requestIp);
            String code = otpService.generateAndSaveOtp(user, OtpChannel.SMS, OtpPurpose.MFA, user.getPhoneE164(), requestIp);
            smsSender.sendOtpSms(user.getPhoneE164(), code);
            smsAbuseProtectionService.recordSmsSent();
            log.info("Sent MFA fallback OTP via SMS to user id={}", user.getId());
        } else {
            throw new BadRequestException("Invalid MFA channel requested: " + channel);
        }
    }

    /**
     * Completes Step 2 of login by validating the second factor and issuing the final tokens.
     */
    @Transactional
    public AuthResponse verifyMfa(MfaVerifyRequest request, String ipAddress, String userAgent) {
        if (ipAddress != null && !rateLimiter.tryAcquire("mfa:verify:ip:" + ipAddress)) {
            throw new com.ayshriv.authplatform.exception.TooManyRequestsException("Too many MFA verification attempts");
        }

        var payload = mfaTokenService.validateMfaToken(request.mfaToken());
        User user = userRepository.findById(payload.userId())
                .orElseThrow(() -> new UnauthorizedException(ErrorMessages.E028));

        if (!user.isAccountActive()) {
            auditService.recordActivity(user.getId(), LogAction.MFA_FAILED, LogSubAction.BLOCKED);
            throw new UnauthorizedException(ErrorMessages.E012);
        }

        String factor = request.factor().trim().toUpperCase();
        boolean verified = false;
        String factorAmr = "mfa";

        switch (factor) {
            case "TOTP" -> {
                if (user.getTotpSecretEncrypted() == null) {
                    throw new ForbiddenException(ErrorMessages.E030);
                }
                String secret = aesGcmEncryptionService.decrypt(user.getTotpSecretEncrypted());
                Optional<Long> stepOpt = totpService.verifyCode(secret, request.code(), user.getTotpLastTimestep());
                if (stepOpt.isPresent()) {
                    user.setTotpLastTimestep(stepOpt.get());
                    userRepository.save(user);
                    verified = true;
                    factorAmr = "mfa_totp";
                }
            }
            case "EMAIL" -> {
                if (!user.isEmailVerified()) {
                    throw new ForbiddenException(ErrorMessages.E030);
                }
                try {
                    otpService.verifyOtp(user.getEmail(), OtpPurpose.MFA, request.code(), ipAddress);
                    verified = true;
                    factorAmr = "mfa_email";
                } catch (Exception e) {
                    verified = false;
                }
            }
            case "SMS" -> {
                if (!user.isPhoneVerified() || user.getPhoneE164() == null) {
                    throw new ForbiddenException(ErrorMessages.E030);
                }
                try {
                    otpService.verifyOtp(user.getPhoneE164(), OtpPurpose.MFA, request.code(), ipAddress);
                    verified = true;
                    factorAmr = "mfa_sms";
                } catch (Exception e) {
                    verified = false;
                }
            }
            case "RECOVERY_CODE" -> {
                verified = recoveryCodeService.validateAndConsumeRecoveryCode(user, request.code());
                if (verified) {
                    auditService.recordActivity(user.getId(), LogAction.RECOVERY_CODE_USED, LogSubAction.SUCCESS);
                    factorAmr = "mfa_recovery";
                }
            }
            default -> throw new BadRequestException("Unsupported MFA factor: " + factor);
        }

        if (!verified) {
            mfaTokenService.recordFailedAttempt(payload.tokenId());
            auditService.recordActivity(user.getId(), LogAction.MFA_FAILED, LogSubAction.FAILURE);
            throw new UnauthorizedException(ErrorMessages.E029);
        }

        // Cleanup token attempts
        mfaTokenService.clearToken(payload.tokenId());

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        List<String> amr = List.of(payload.initialFactor(), factorAmr);
        auditService.recordActivity(user.getId(), LogAction.MFA_VERIFY, LogSubAction.SUCCESS);
        log.info("MFA verification successful for user id={}, factor={}", user.getId(), factor);

        return authenticationResultService.issueTokensWithClaims(
                user,
                amr,
                Instant.now(),
                LogAction.LOGIN,
                ipAddress,
                userAgent
        );
    }
}
