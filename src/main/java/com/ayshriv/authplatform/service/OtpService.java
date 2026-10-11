package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.LogSubAction;
import com.ayshriv.authplatform.entity.OtpChallenge;
import com.ayshriv.authplatform.entity.OtpChannel;
import com.ayshriv.authplatform.entity.OtpPurpose;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.exception.BadRequestException;
import com.ayshriv.authplatform.exception.TooManyRequestsException;
import com.ayshriv.authplatform.repository.OtpChallengeRepository;
import com.ayshriv.authplatform.security.RateLimiter;
import com.ayshriv.authplatform.util.LogMaskingUtils;
import com.ayshriv.authplatform.util.OtpCryptoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpChallengeRepository otpChallengeRepository;
    private final RateLimiter rateLimiter;
    private final AuditService auditService;
    private final AppProperties appProperties;

    public OtpService(OtpChallengeRepository otpChallengeRepository,
                      RateLimiter rateLimiter,
                      AuditService auditService,
                      AppProperties appProperties) {
        this.otpChallengeRepository = otpChallengeRepository;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.appProperties = appProperties;
    }

    @Transactional
    public String generateAndSaveOtp(User user, OtpChannel channel, OtpPurpose purpose, String destination, String requestIp) {
        String normalizedDestination = destination.trim().toLowerCase();
        Long userId = user != null ? user.getId() : null;

        // Rate limiting check: IP rate limit
        if (requestIp != null && !rateLimiter.tryAcquire("ip:" + requestIp)) {
            log.warn("Rate limit exceeded for IP: {}", requestIp);
            auditService.recordActivity(userId, LogAction.OTP_REQUEST, LogSubAction.RATE_LIMITED);
            throw new TooManyRequestsException("Too many OTP requests from this IP address");
        }

        // Rate limiting check: Destination rate limit
        if (!rateLimiter.tryAcquire("dest:" + normalizedDestination)) {
            log.warn("Rate limit exceeded for destination: {}", LogMaskingUtils.maskEmail(normalizedDestination));
            auditService.recordActivity(userId, LogAction.OTP_REQUEST, LogSubAction.RATE_LIMITED);
            throw new TooManyRequestsException("Too many OTP requests for this destination");
        }

        // Check DB-based sends per hour
        Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
        long sendsInLastHour = otpChallengeRepository.countByDestinationAndCreatedOnAfter(normalizedDestination, oneHourAgo);
        if (sendsInLastHour >= appProperties.otp().maxSendsPerHourPerDestination()) {
            log.warn("Exceeded max sends per hour ({}) for destination: {}",
                    appProperties.otp().maxSendsPerHourPerDestination(), LogMaskingUtils.maskEmail(normalizedDestination));
            auditService.recordActivity(userId, LogAction.OTP_REQUEST, LogSubAction.RATE_LIMITED);
            throw new TooManyRequestsException("Too many OTP requests for this destination in the last hour");
        }

        // Check active challenge cooldown
        Optional<OtpChallenge> existingOpt = otpChallengeRepository
                .findTopByDestinationAndPurposeAndActiveTrueAndDeletedFalseOrderByCreatedOnDesc(normalizedDestination, purpose);

        if (existingOpt.isPresent()) {
            OtpChallenge existing = existingOpt.get();
            Duration durationSinceCreated = Duration.between(existing.getCreatedOn(), Instant.now());
            if (durationSinceCreated.getSeconds() < appProperties.otp().resendCooldownSeconds()) {
                long remainingSeconds = appProperties.otp().resendCooldownSeconds() - durationSinceCreated.getSeconds();
                log.warn("Cooldown active for destination: {}, retry after {}s", LogMaskingUtils.maskEmail(normalizedDestination), remainingSeconds);
                auditService.recordActivity(userId, LogAction.OTP_REQUEST, LogSubAction.RATE_LIMITED);
                throw new TooManyRequestsException("Please wait " + remainingSeconds + " seconds before requesting a new code");
            }
        }

        // Deactivate existing challenges for this user/destination and purpose (ensures one active challenge per user/dest & purpose)
        if (userId != null) {
            otpChallengeRepository.deactivateExistingChallengesByUserId(userId, purpose);
        }
        otpChallengeRepository.deactivateExistingChallengesByDestination(normalizedDestination, purpose);

        // Generate 6-digit code
        String plainCode = OtpCryptoUtils.generate6DigitCode();
        String codeHash = OtpCryptoUtils.hashOtp(plainCode, normalizedDestination, appProperties.otp().pepper());
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(appProperties.otp().ttlMinutes()));

        OtpChallenge challenge = new OtpChallenge(
                user,
                channel,
                purpose,
                normalizedDestination,
                codeHash,
                expiresAt,
                appProperties.otp().maxAttempts(),
                requestIp
        );

        otpChallengeRepository.save(challenge);
        auditService.recordActivity(userId, LogAction.OTP_REQUEST, LogSubAction.CHALLENGE_ISSUED);
        log.info("Issued OTP challenge for purpose: {}, destination: {}", purpose, LogMaskingUtils.maskEmail(normalizedDestination));

        return plainCode;
    }

    @Transactional
    public OtpChallenge verifyOtp(String destination, OtpPurpose purpose, String submittedCode, String requestIp) {
        String normalizedDestination = destination.trim().toLowerCase();

        // Rate limiting check: IP rate limit
        if (requestIp != null && !rateLimiter.tryAcquire("ip:verify:" + requestIp)) {
            log.warn("Rate limit exceeded for OTP verification from IP: {}", requestIp);
            throw new TooManyRequestsException("Too many verification attempts from this IP address");
        }

        Optional<OtpChallenge> challengeOpt = otpChallengeRepository
                .findTopByDestinationAndPurposeAndActiveTrueAndDeletedFalseOrderByCreatedOnDesc(normalizedDestination, purpose);

        if (challengeOpt.isEmpty()) {
            // Anti-enumeration / security: timing delay or generic message
            log.warn("No active OTP challenge found for destination: {}, purpose: {}", LogMaskingUtils.maskEmail(normalizedDestination), purpose);
            throw new BadRequestException(ErrorMessages.E015);
        }

        OtpChallenge challenge = challengeOpt.get();
        Long userId = challenge.getUser() != null ? challenge.getUser().getId() : null;

        // Check if already expired
        if (challenge.isExpired()) {
            challenge.setActive(false);
            otpChallengeRepository.save(challenge);
            auditService.recordActivity(userId, LogAction.OTP_VERIFY, LogSubAction.EXPIRED);
            log.warn("OTP challenge expired for destination: {}", LogMaskingUtils.maskEmail(normalizedDestination));
            throw new BadRequestException(ErrorMessages.E015);
        }

        // Check if attempts already exhausted
        if (challenge.isAttemptsExhausted()) {
            challenge.setActive(false);
            otpChallengeRepository.save(challenge);
            auditService.recordActivity(userId, LogAction.OTP_VERIFY, LogSubAction.BLOCKED);
            log.warn("OTP attempts exhausted for destination: {}", LogMaskingUtils.maskEmail(normalizedDestination));
            throw new BadRequestException(ErrorMessages.E017);
        }

        // Increment attempts count
        challenge.setAttempts(challenge.getAttempts() + 1);

        // Constant-time comparison using HMAC-SHA256
        String submittedHash = OtpCryptoUtils.hashOtp(submittedCode.trim(), normalizedDestination, appProperties.otp().pepper());
        boolean matches = OtpCryptoUtils.constantTimeEquals(submittedHash, challenge.getCodeHash());

        if (!matches) {
            if (challenge.isAttemptsExhausted()) {
                challenge.setActive(false);
                otpChallengeRepository.save(challenge);
                auditService.recordActivity(userId, LogAction.OTP_VERIFY, LogSubAction.BLOCKED);
                log.warn("OTP attempt failed and attempts exhausted for destination: {}", LogMaskingUtils.maskEmail(normalizedDestination));
                throw new BadRequestException(ErrorMessages.E017);
            }
            otpChallengeRepository.save(challenge);
            auditService.recordActivity(userId, LogAction.OTP_VERIFY, LogSubAction.FAILURE);
            log.warn("OTP verification attempt failed for destination: {}, attempt {}/{}",
                    LogMaskingUtils.maskEmail(normalizedDestination), challenge.getAttempts(), challenge.getMaxAttempts());
            throw new BadRequestException(ErrorMessages.E015);
        }

        // Verified successfully: mark consumed and inactive
        challenge.setConsumedAt(Instant.now());
        challenge.setActive(false);
        otpChallengeRepository.save(challenge);

        auditService.recordActivity(userId, LogAction.OTP_VERIFY, LogSubAction.SUCCESS);
        log.info("OTP successfully verified for destination: {}, purpose: {}", LogMaskingUtils.maskEmail(normalizedDestination), purpose);

        return challenge;
    }
}
