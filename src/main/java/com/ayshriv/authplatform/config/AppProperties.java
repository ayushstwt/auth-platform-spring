package com.ayshriv.authplatform.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull SecurityProperties security,
        @NotNull OtpProperties otp,
        @NotNull EmailProperties email
) {
    public record SecurityProperties(
            @NotNull CorsProperties cors,
            @NotNull JwtProperties jwt,
            @NotNull AuthProperties auth
    ) {
    }

    public record CorsProperties(
            @NotEmpty List<String> allowedOrigins,
            @NotEmpty List<String> allowedMethods,
            @NotEmpty List<String> allowedHeaders,
            boolean allowCredentials,
            long maxAgeSeconds
    ) {
    }

    public record JwtProperties(
            @NotEmpty String issuer,
            @NotEmpty String audience,
            long accessTokenExpiryMinutes,
            long refreshTokenExpiryDays,
            String privateKeyLocation,
            String publicKeyLocation
    ) {
    }

    public record AuthProperties(
            int maxFailedAttempts,
            long lockoutDurationMinutes,
            boolean requireVerifiedEmailBeforeLogin
    ) {
    }

    public record OtpProperties(
            @NotEmpty String pepper,
            long ttlMinutes,
            int maxAttempts,
            long resendCooldownSeconds,
            int maxSendsPerHourPerDestination
    ) {
    }

    public record EmailProperties(
            @NotEmpty String fromAddress,
            @NotEmpty String fromName
    ) {
    }
}
