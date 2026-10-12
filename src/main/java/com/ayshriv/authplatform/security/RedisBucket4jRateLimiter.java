package com.ayshriv.authplatform.security;

import com.ayshriv.authplatform.config.AppProperties;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Primary
@ConditionalOnProperty(name = "app.security.rate-limit.type", havingValue = "redis")
public class RedisBucket4jRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisBucket4jRateLimiter.class);

    private final Bucket4jRateLimiter fallbackLimiter;
    private final LettuceBasedProxyManager<byte[]> proxyManager;

    @SuppressWarnings("unchecked")
    public RedisBucket4jRateLimiter(Bucket4jRateLimiter fallbackLimiter, LettuceConnectionFactory connectionFactory) {
        this.fallbackLimiter = fallbackLimiter;
        LettuceBasedProxyManager<byte[]> manager = null;
        try {
            var clientField = LettuceConnectionFactory.class.getDeclaredField("client");
            clientField.setAccessible(true);
            Object clientObj = clientField.get(connectionFactory);
            if (clientObj instanceof RedisClient redisClient) {
                manager = LettuceBasedProxyManager.builderFor(redisClient)
                        .build();
                log.info("Initialized Redis-backed Bucket4j rate limiter via Lettuce");
            }
        } catch (Exception e) {
            log.warn("Could not extract RedisClient directly from LettuceConnectionFactory: {}. Redis rate limiter will fallback to in-memory.", e.getMessage());
        }
        this.proxyManager = manager;
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
        if (proxyManager == null) {
            return fallbackLimiter.tryConsumeWithResult(key, tokens);
        }
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            BucketConfiguration config = fallbackLimiter.resolveConfigurationForKey(key);
            var bucket = proxyManager.builder().build(keyBytes, () -> config);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(tokens);
            if (probe.isConsumed()) {
                return RateLimitResult.consumed(probe.getRemainingTokens());
            } else {
                long nanosToWait = probe.getNanosToWaitForRefill();
                long secondsToWait = Math.max(1, (nanosToWait + 999_999_999L) / 1_000_000_000L);
                return RateLimitResult.rejected(secondsToWait);
            }
        } catch (Exception ex) {
            log.warn("Redis rate limiter evaluation failed for key {}: {}. Falling back to in-memory limiter.", key, ex.getMessage());
            return fallbackLimiter.tryConsumeWithResult(key, tokens);
        }
    }
}
