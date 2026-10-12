package com.ayshriv.authplatform.security;

import com.ayshriv.authplatform.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class Bucket4jRateLimiter implements RateLimiter {

    private final AppProperties appProperties;
    private final Map<String, io.github.bucket4j.Bucket> inMemoryBuckets = new ConcurrentHashMap<>();

    public Bucket4jRateLimiter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    @Override
    public boolean tryAcquire(String key, int tokens) {
        return tryConsumeWithResult(key, tokens).allowed();
    }

    @Override
    public RateLimitResult tryConsumeWithResult(String key, int tokens) {
        io.github.bucket4j.Bucket bucket = inMemoryBuckets.computeIfAbsent(key, this::createInMemoryBucket);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(tokens);
        if (probe.isConsumed()) {
            return RateLimitResult.consumed(probe.getRemainingTokens());
        } else {
            long nanosToWait = probe.getNanosToWaitForRefill();
            long secondsToWait = Math.max(1, (nanosToWait + 999_999_999L) / 1_000_000_000L);
            return RateLimitResult.rejected(secondsToWait);
        }
    }

    private io.github.bucket4j.Bucket createInMemoryBucket(String key) {
        return io.github.bucket4j.Bucket.builder()
                .addLimit(resolveLimitForKey(key))
                .build();
    }

    public Bandwidth resolveLimitForKey(String key) {
        var rateProps = appProperties.security().rateLimit();
        if (key.startsWith("rate:auth:") || key.startsWith("ip:")) {
            return Bandwidth.builder()
                    .capacity(rateProps.authRequestsPerMinute())
                    .refillGreedy(rateProps.authRequestsPerMinute(), Duration.ofMinutes(1))
                    .build();
        } else if (key.startsWith("rate:otp:")) {
            return Bandwidth.builder()
                    .capacity(rateProps.otpRequestsPerMinute())
                    .refillGreedy(rateProps.otpRequestsPerMinute(), Duration.ofMinutes(1))
                    .build();
        } else if (key.startsWith("rate:admin:")) {
            return Bandwidth.builder()
                    .capacity(rateProps.adminRequestsPerMinute())
                    .refillGreedy(rateProps.adminRequestsPerMinute(), Duration.ofMinutes(1))
                    .build();
        } else if (key.startsWith("sms:ip:")) {
            return Bandwidth.builder()
                    .capacity(appProperties.sms().maxSendsPerHourPerIp())
                    .refillGreedy(appProperties.sms().maxSendsPerHourPerIp(), Duration.ofHours(1))
                    .build();
        } else if (key.startsWith("sms:user:")) {
            return Bandwidth.builder()
                    .capacity(appProperties.sms().maxSendsPerHourPerUser())
                    .refillGreedy(appProperties.sms().maxSendsPerHourPerUser(), Duration.ofHours(1))
                    .build();
        } else if (key.equals("sms:global")) {
            return Bandwidth.builder()
                    .capacity(appProperties.sms().globalHourlyCap())
                    .refillGreedy(appProperties.sms().globalHourlyCap(), Duration.ofHours(1))
                    .build();
        } else if (key.startsWith("dest:")) {
            return Bandwidth.builder()
                    .capacity(appProperties.otp().maxSendsPerHourPerDestination())
                    .refillGreedy(appProperties.otp().maxSendsPerHourPerDestination(), Duration.ofHours(1))
                    .build();
        } else {
            return Bandwidth.builder()
                    .capacity(rateProps.defaultRequestsPerMinute())
                    .refillGreedy(rateProps.defaultRequestsPerMinute(), Duration.ofMinutes(1))
                    .build();
        }
    }

    public BucketConfiguration resolveConfigurationForKey(String key) {
        return BucketConfiguration.builder()
                .addLimit(resolveLimitForKey(key))
                .build();
    }
}
