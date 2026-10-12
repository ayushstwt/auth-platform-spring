package com.ayshriv.authplatform.security;

public record RateLimitResult(
        boolean allowed,
        long tokensRemaining,
        long retryAfterSeconds
) {
    public static RateLimitResult consumed(long tokensRemaining) {
        return new RateLimitResult(true, tokensRemaining, 0);
    }

    public static RateLimitResult rejected(long retryAfterSeconds) {
        return new RateLimitResult(false, 0, Math.max(1, retryAfterSeconds));
    }
}
