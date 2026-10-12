package com.ayshriv.authplatform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SecurityMetrics {

    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter otpSentCounter;
    private final Counter otpVerifiedCounter;
    private final Counter lockoutCounter;
    private final Counter providerErrorCounter;

    public SecurityMetrics(MeterRegistry meterRegistry) {
        this.loginSuccessCounter = Counter.builder("auth.logins.success")
                .description("Number of successful user logins")
                .register(meterRegistry);

        this.loginFailureCounter = Counter.builder("auth.logins.failure")
                .description("Number of failed user logins")
                .register(meterRegistry);

        this.otpSentCounter = Counter.builder("auth.otp.sent")
                .description("Number of OTP verification codes dispatched")
                .register(meterRegistry);

        this.otpVerifiedCounter = Counter.builder("auth.otp.verified")
                .description("Number of OTP verification codes successfully verified")
                .register(meterRegistry);

        this.lockoutCounter = Counter.builder("auth.account.locked")
                .description("Number of user accounts locked due to failed attempts")
                .register(meterRegistry);

        this.providerErrorCounter = Counter.builder("auth.provider.error")
                .description("Number of third-party external provider failures (SMS, Mail, Google)")
                .register(meterRegistry);
    }

    public void incrementLoginSuccess() {
        loginSuccessCounter.increment();
    }

    public void incrementLoginFailure() {
        loginFailureCounter.increment();
    }

    public void incrementOtpSent() {
        otpSentCounter.increment();
    }

    public void incrementOtpVerified() {
        otpVerifiedCounter.increment();
    }

    public void incrementLockout() {
        lockoutCounter.increment();
    }

    public void incrementProviderError() {
        providerErrorCounter.increment();
    }
}
