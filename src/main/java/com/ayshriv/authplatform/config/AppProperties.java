package com.ayshriv.authplatform.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull SecurityProperties security,
        @NotNull OtpProperties otp,
        @NotNull EmailProperties email,
        @NotNull SmsProperties sms,
        @NotNull GoogleProperties google
) {
    public record SecurityProperties(
            @NotNull CorsProperties cors,
            @NotNull JwtProperties jwt,
            @NotNull AuthProperties auth,
            RateLimitProperties rateLimit,
            TokenDenylistProperties tokenDenylist
    ) {
        @ConstructorBinding
        public SecurityProperties(
                CorsProperties cors,
                JwtProperties jwt,
                AuthProperties auth,
                RateLimitProperties rateLimit,
                TokenDenylistProperties tokenDenylist
        ) {
            this.cors = cors;
            this.jwt = jwt;
            this.auth = auth;
            this.rateLimit = rateLimit != null ? rateLimit : new RateLimitProperties("in-memory", 30, 10, 120, 60);
            this.tokenDenylist = tokenDenylist != null ? tokenDenylist : new TokenDenylistProperties(true, 15);
        }

        public SecurityProperties(CorsProperties cors, JwtProperties jwt, AuthProperties auth) {
            this(cors, jwt, auth, new RateLimitProperties("in-memory", 30, 10, 120, 60), new TokenDenylistProperties(true, 15));
        }
    }

    public record RateLimitProperties(
            String type, // "in-memory" or "redis"
            int authRequestsPerMinute,
            int otpRequestsPerMinute,
            int adminRequestsPerMinute,
            int defaultRequestsPerMinute
    ) {
        public RateLimitProperties {
            if (type == null || type.isBlank()) {
                type = "in-memory";
            }
            if (authRequestsPerMinute <= 0) {
                authRequestsPerMinute = 30;
            }
            if (otpRequestsPerMinute <= 0) {
                otpRequestsPerMinute = 10;
            }
            if (adminRequestsPerMinute <= 0) {
                adminRequestsPerMinute = 120;
            }
            if (defaultRequestsPerMinute <= 0) {
                defaultRequestsPerMinute = 60;
            }
        }
    }

    public record TokenDenylistProperties(
            boolean enabled,
            long cacheTtlMinutes
    ) {
        public TokenDenylistProperties {
            if (cacheTtlMinutes <= 0) {
                cacheTtlMinutes = 15;
            }
        }
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
}
