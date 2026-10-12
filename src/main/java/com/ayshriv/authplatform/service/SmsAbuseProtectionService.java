package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.exception.ForbiddenException;
import com.ayshriv.authplatform.exception.TooManyRequestsException;
import com.ayshriv.authplatform.repository.OtpChallengeRepository;
import com.ayshriv.authplatform.security.RateLimiter;
import com.ayshriv.authplatform.util.LogMaskingUtils;
import com.ayshriv.authplatform.util.PhoneNumberValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SmsAbuseProtectionService {

    private static final Logger log = LoggerFactory.getLogger(SmsAbuseProtectionService.class);

    private final RateLimiter rateLimiter;
    private final OtpChallengeRepository otpChallengeRepository;
    private final AppProperties appProperties;

    private final AtomicInteger dailyCostGuardCounter = new AtomicInteger(0);

    public SmsAbuseProtectionService(RateLimiter rateLimiter,
                                     OtpChallengeRepository otpChallengeRepository,
                                     AppProperties appProperties) {
        this.rateLimiter = rateLimiter;
        this.otpChallengeRepository = otpChallengeRepository;
        this.appProperties = appProperties;
    }

    public void validateSmsSendAllowed(String phoneE164, Long userId, String requestIp) {
        // 1. Blocked prefixes (e.g. premium rate numbers, toll fraud numbers)
        for (String blockedPrefix : appProperties.sms().blockedPrefixes()) {
            if (phoneE164.startsWith(blockedPrefix)) {
                log.warn("Blocked prefix detected: phone {}", LogMaskingUtils.maskPhone(phoneE164));
                throw new ForbiddenException(ErrorMessages.E019);
            }
        }

        // 2. Allowed country code check
        String region = PhoneNumberValidationUtils.getRegionCodeForNumber(phoneE164);
        if (region == null || !appProperties.sms().allowedCountryCodes().contains(region)) {
            log.warn("Phone region '{}' not in allowed list: phone {}", region, LogMaskingUtils.maskPhone(phoneE164));
            throw new ForbiddenException(ErrorMessages.E019);
        }

        // 3. Daily cost-guard check
        if (dailyCostGuardCounter.get() >= appProperties.sms().dailyCostGuardLimit()) {
            log.error("ALERT: Daily SMS cost-guard limit ({}) exceeded! Blocking outbound SMS.",
                    appProperties.sms().dailyCostGuardLimit());
            throw new TooManyRequestsException(ErrorMessages.E021.getMessage());
        }

        // 4. IP rate limit check (e.g. max 20 per hour per IP)
        if (requestIp != null && !rateLimiter.tryAcquire("sms:ip:" + requestIp)) {
            log.warn("SMS IP hourly rate limit reached: {}", requestIp);
            throw new TooManyRequestsException(ErrorMessages.E016.getMessage());
        }

        // 5. User rate limit check (e.g. max 5 per hour per user)
        if (userId != null && !rateLimiter.tryAcquire("sms:user:" + userId)) {
            log.warn("SMS User hourly rate limit reached: user id={}", userId);
            throw new TooManyRequestsException(ErrorMessages.E016.getMessage());
        }

        // 6. Global hourly cap check
        if (!rateLimiter.tryAcquire("sms:global")) {
            log.warn("SMS global hourly cap reached!");
            throw new TooManyRequestsException(ErrorMessages.E016.getMessage());
        }

        // 7. Per-phone hourly count check from DB
        Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));
        long count = otpChallengeRepository.countByDestinationAndCreatedOnAfter(phoneE164, oneHourAgo);
        if (count >= appProperties.sms().maxSendsPerHourPerPhone()) {
            log.warn("Phone {} reached hourly send cap ({})", LogMaskingUtils.maskPhone(phoneE164), appProperties.sms().maxSendsPerHourPerPhone());
            throw new TooManyRequestsException(ErrorMessages.E016.getMessage());
        }
    }

    public void recordSmsSent() {
        dailyCostGuardCounter.incrementAndGet();
    }

    public int getDailySentCount() {
        return dailyCostGuardCounter.get();
    }

    public void resetDailyCounter() {
        dailyCostGuardCounter.set(0);
    }

    @Scheduled(cron = "0 0 0 * * *") // Reset daily at midnight UTC
    public void resetDailyCostGuardSchedule() {
        int previous = dailyCostGuardCounter.getAndSet(0);
        log.info("Reset daily SMS cost guard counter. Previous day total: {}", previous);
    }
}
