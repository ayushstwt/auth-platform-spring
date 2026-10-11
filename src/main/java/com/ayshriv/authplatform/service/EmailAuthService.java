package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.dto.AuthResponse;
import com.ayshriv.authplatform.dto.EmailOtpConfirmRequest;
import com.ayshriv.authplatform.dto.EmailOtpSendRequest;
import com.ayshriv.authplatform.dto.ForgotPasswordRequest;
import com.ayshriv.authplatform.dto.LoginResult;
import com.ayshriv.authplatform.dto.ResetPasswordRequest;
import com.ayshriv.authplatform.dto.UserResponse;
import com.ayshriv.authplatform.dto.VerifyEmailConfirmRequest;
import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.LogSubAction;
import com.ayshriv.authplatform.entity.OtpChallenge;
import com.ayshriv.authplatform.entity.OtpChannel;
import com.ayshriv.authplatform.entity.OtpPurpose;
import com.ayshriv.authplatform.entity.RefreshToken;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.exception.ResourceNotFoundException;
import com.ayshriv.authplatform.exception.UnauthorizedException;
import com.ayshriv.authplatform.repository.RefreshTokenRepository;
import com.ayshriv.authplatform.repository.UserRepository;
import com.ayshriv.authplatform.security.JwtTokenService;
import com.ayshriv.authplatform.security.PasswordPolicyValidator;
import com.ayshriv.authplatform.util.LogMaskingUtils;
import com.ayshriv.authplatform.util.OtpCryptoUtils;
import com.ayshriv.authplatform.util.TokenHashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class EmailAuthService {

    private static final Logger log = LoggerFactory.getLogger(EmailAuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final EmailSender emailSender;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuditService auditService;
    private final TokenRevocationService tokenRevocationService;
    private final AuthenticationResultService authenticationResultService;
    private final AppProperties appProperties;

    public EmailAuthService(UserRepository userRepository,
                            RefreshTokenRepository refreshTokenRepository,
                            OtpService otpService,
                            EmailSender emailSender,
                            JwtTokenService jwtTokenService,
                            PasswordEncoder passwordEncoder,
                            PasswordPolicyValidator passwordPolicyValidator,
                            AuditService auditService,
                            TokenRevocationService tokenRevocationService,
                            AuthenticationResultService authenticationResultService,
                            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpService = otpService;
        this.emailSender = emailSender;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.auditService = auditService;
        this.tokenRevocationService = tokenRevocationService;
        this.authenticationResultService = authenticationResultService;
        this.appProperties = appProperties;
    }


    @Transactional
    public void sendEmailVerificationOtp(Long userId, String requestIp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        String code = otpService.generateAndSaveOtp(
                user,
                OtpChannel.EMAIL,
                OtpPurpose.EMAIL_VERIFY,
                user.getEmail(),
                requestIp
        );

        emailSender.sendVerificationEmail(user.getEmail(), user.getFirstName(), code);
        log.info("Sent email verification OTP to user id={}", user.getId());
    }

    @Transactional
    public void confirmEmailVerification(Long userId, VerifyEmailConfirmRequest request, String requestIp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        otpService.verifyOtp(user.getEmail(), OtpPurpose.EMAIL_VERIFY, request.code(), requestIp);

        user.setEmailVerified(true);
        userRepository.save(user);

        auditService.recordActivity(user.getId(), LogAction.EMAIL_VERIFY, LogSubAction.SUCCESS);
        log.info("Email verified successfully for user id={}", user.getId());
    }

    @Transactional
    public void sendLoginOtp(EmailOtpSendRequest request, String requestIp) {
        String normalizedEmail = request.email().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.isAccountActive()) {
                String code = otpService.generateAndSaveOtp(
                        user,
                        OtpChannel.EMAIL,
                        OtpPurpose.LOGIN,
                        normalizedEmail,
                        requestIp
                );
                emailSender.sendLoginOtpEmail(normalizedEmail, user.getFirstName(), code);
                log.info("Sent login OTP to user id={}", user.getId());
                return;
            } else {
                log.warn("Login OTP requested for inactive user id={}", user.getId());
                auditService.recordActivity(user.getId(), LogAction.LOGIN, LogSubAction.BLOCKED);
            }
        } else {
            // Anti-enumeration dummy calculation to mimic hashing timing
            OtpCryptoUtils.hashOtp("123456", normalizedEmail, appProperties.otp().pepper());
            log.info("Anti-enumeration: Login OTP requested for non-existent destination {}", LogMaskingUtils.maskEmail(normalizedEmail));
        }
    }

    @Transactional
    public LoginResult confirmLoginOtp(EmailOtpConfirmRequest request, String requestIp, String userAgent) {
        String normalizedEmail = request.email().trim().toLowerCase();

        OtpChallenge challenge = otpService.verifyOtp(normalizedEmail, OtpPurpose.LOGIN, request.code(), requestIp);
        User user = challenge.getUser();

        if (user == null) {
            user = userRepository.findByEmail(normalizedEmail)
                    .orElseThrow(() -> new UnauthorizedException(ErrorMessages.E012));
        }

        if (!user.isAccountActive()) {
            auditService.recordActivity(user.getId(), LogAction.LOGIN, LogSubAction.BLOCKED);
            throw new UnauthorizedException(ErrorMessages.E012);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return authenticationResultService.processAuthentication(user, "email_otp", LogAction.LOGIN, requestIp, userAgent);
    }


    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, String requestIp) {
        String normalizedEmail = request.email().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.isAccountActive()) {
                String code = otpService.generateAndSaveOtp(
                        user,
                        OtpChannel.EMAIL,
                        OtpPurpose.PASSWORD_RESET,
                        normalizedEmail,
                        requestIp
                );
                emailSender.sendPasswordResetEmail(normalizedEmail, user.getFirstName(), code);
                log.info("Sent password reset OTP to user id={}", user.getId());
                return;
            } else {
                log.warn("Password reset requested for locked/inactive user id={}", user.getId());
                auditService.recordActivity(user.getId(), LogAction.PASSWORD_RESET, LogSubAction.BLOCKED);
            }
        } else {
            // Anti-enumeration dummy calculation
            OtpCryptoUtils.hashOtp("123456", normalizedEmail, appProperties.otp().pepper());
            log.info("Anti-enumeration: Forgot password requested for non-existent destination {}", LogMaskingUtils.maskEmail(normalizedEmail));
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request, String requestIp) {
        String normalizedEmail = request.email().trim().toLowerCase();
        passwordPolicyValidator.validate(request.newPassword());

        OtpChallenge challenge = otpService.verifyOtp(normalizedEmail, OtpPurpose.PASSWORD_RESET, request.code(), requestIp);
        User user = challenge.getUser();

        if (user == null) {
            user = userRepository.findByEmail(normalizedEmail)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, normalizedEmail));
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        user.setHasPassword(true);
        userRepository.save(user);

        // Revoke all existing sessions and refresh tokens upon password reset
        refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());

        auditService.recordActivity(user.getId(), LogAction.PASSWORD_RESET, LogSubAction.SUCCESS);
        log.info("Password successfully reset and sessions revoked for user id={}", user.getId());
    }
}
