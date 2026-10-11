package com.ayshriv.authplatform.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class Bucket4jRateLimiter implements RateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key) {
        return tryAcquire(key, 1);
    }

    @Override
    public boolean tryAcquire(String key, int tokens) {
        Bucket bucket = buckets.computeIfAbsent(key, this::createBucketForKey);
        return bucket.tryConsume(tokens);
    }

    private Bucket createBucketForKey(String key) {
        if (key.startsWith("ip:")) {
            // IP rate limit: 30 requests per minute
            return Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(30).refillGreedy(30, Duration.ofMinutes(1)).build())
                    .build();
        } else if (key.startsWith("sms:ip:")) {
            // SMS IP limit: 20 requests per hour
            return Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(20).refillGreedy(20, Duration.ofHours(1)).build())
                    .build();
        } else if (key.startsWith("sms:user:")) {
            // SMS user limit: 5 requests per hour
            return Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofHours(1)).build())
                    .build();
        } else if (key.equals("sms:global")) {
            // SMS global hourly cap: 100 per hour
            return Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(100).refillGreedy(100, Duration.ofHours(1)).build())
                    .build();
        } else if (key.startsWith("dest:")) {
            // Destination rate limit: 5 requests per hour (aligned with maxSendsPerHour)
            return Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(5).refillGreedy(5, Duration.ofHours(1)).build())
                    .build();
        } else {
            // Default: 60 requests per minute
            return Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(60).refillGreedy(60, Duration.ofMinutes(1)).build())
                    .build();
        }
    }
}
