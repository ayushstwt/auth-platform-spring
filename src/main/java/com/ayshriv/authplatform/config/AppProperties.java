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
        @NotNull EmailProperties email,
        @NotNull SmsProperties sms,
        @NotNull GoogleProperties google,
        @NotNull MfaProperties mfa
) {
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public AppProperties(
            SecurityProperties security,
            OtpProperties otp,
            EmailProperties email,
            SmsProperties sms,
            GoogleProperties google,
            MfaProperties mfa
    ) {
        this.security = security;
        this.otp = otp;
        this.email = email;
        this.sms = sms;
        this.google = google;
        this.mfa = mfa;
    }

    public AppProperties(
            SecurityProperties security,
            OtpProperties otp,
            EmailProperties email,
            SmsProperties sms,
            GoogleProperties google
    ) {

        this(
                security,
                otp,
                email,
                sms,
                google,
                new MfaProperties("k1", java.util.Map.of("k1", "01234567890123456789012345678901"), 5, 5, 15, false, "AuthPlatform")
        );
    }

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
            boolean requireVerifiedEmailBeforeLogin,
            long reauthValidityMinutes
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

    public record SmsProperties(
            @NotEmpty String provider,
            @NotEmpty String defaultRegion,
            @NotEmpty List<String> allowedCountryCodes,
            @NotNull List<String> blockedPrefixes,
            int maxSendsPerHourPerPhone,
            int maxSendsPerHourPerIp,
            int maxSendsPerHourPerUser,
            int globalHourlyCap,
            int dailyCostGuardLimit,
            @NotNull TwilioProperties twilio
    ) {
    }

    public record TwilioProperties(
            String accountSid,
            String authToken,
            String fromPhoneNumber
    ) {
    }

    public record GoogleProperties(
            @NotNull List<String> clientIds,
            String hostedDomain,
            boolean redirectFlowEnabled,
            @NotEmpty String redirectSuccessUrl,
            long exchangeCodeTtlSeconds,
            @NotEmpty String jwksUri
    ) {
    }

    public record MfaProperties(
            @NotEmpty String currentKeyId,
            @NotEmpty java.util.Map<String, String> encryptionKeys,
            long tokenTtlMinutes,
            int maxAttempts,
            long stepUpValidityMinutes,
            boolean enforceForAdmin,
            @NotEmpty String issuer
    ) {
    }
}
