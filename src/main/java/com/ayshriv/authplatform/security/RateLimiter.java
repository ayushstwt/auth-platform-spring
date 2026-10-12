package com.ayshriv.authplatform.security;

public interface RateLimiter {

    boolean tryAcquire(String key);

    boolean tryAcquire(String key, int tokens);

    default RateLimitResult tryConsumeWithResult(String key, int tokens) {
        boolean acquired = tryAcquire(key, tokens);
        return acquired ? RateLimitResult.consumed(0) : RateLimitResult.rejected(60);
    }
}
