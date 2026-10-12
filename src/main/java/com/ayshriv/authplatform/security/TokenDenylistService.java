package com.ayshriv.authplatform.security;

import com.ayshriv.authplatform.config.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class TokenDenylistService {

    private static final Logger log = LoggerFactory.getLogger(TokenDenylistService.class);

    private final AppProperties appProperties;
    private final Cache<String, Boolean> localCache;
    private final StringRedisTemplate redisTemplate;

    public TokenDenylistService(AppProperties appProperties,
                                @org.springframework.beans.factory.annotation.Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.appProperties = appProperties;
        this.redisTemplate = redisTemplate;
        long ttlMinutes = appProperties.security().tokenDenylist().cacheTtlMinutes();
        this.localCache = Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(50_000)
                .build();
    }

    public void revokeJti(String jti) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        localCache.put(jti, Boolean.TRUE);
        if (redisTemplate != null) {
            try {
                long ttlMinutes = appProperties.security().tokenDenylist().cacheTtlMinutes();
                redisTemplate.opsForValue().set("denylist:jti:" + jti, "1", Duration.ofMinutes(ttlMinutes));
            } catch (Exception e) {
                log.warn("Failed to write revoked JTI to Redis: {}", e.getMessage());
            }
        }
        log.info("Access token revoked via JTI denylist: {}", jti);
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank() || !appProperties.security().tokenDenylist().enabled()) {
            return false;
        }
        if (Boolean.TRUE.equals(localCache.getIfPresent(jti))) {
            return true;
        }
        if (redisTemplate != null) {
            try {
                Boolean hasKey = redisTemplate.hasKey("denylist:jti:" + jti);
                if (Boolean.TRUE.equals(hasKey)) {
                    localCache.put(jti, Boolean.TRUE);
                    return true;
                }
            } catch (Exception e) {
                log.warn("Failed to check Redis JTI denylist: {}", e.getMessage());
            }
        }
        return false;
    }
}
