package com.ayshriv.authplatform.security;

public interface RateLimiter {

    boolean tryAcquire(String key);

    boolean tryAcquire(String key, int tokens);
}
