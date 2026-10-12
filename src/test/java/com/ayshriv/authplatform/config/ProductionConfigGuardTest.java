package com.ayshriv.authplatform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionConfigGuardTest {

    @Test
    @DisplayName("Should fail when OTP pepper is insecure default or short")
    void shouldFailWhenOtpPepperIsInsecure() {
        AppProperties.OtpProperties otp = new AppProperties.OtpProperties(
                "default-otp-secret-pepper-change-in-production-min-32-bytes!", 5, 5, 60, 5);
        AppProperties.SecurityProperties sec = new AppProperties.SecurityProperties(
                new AppProperties.CorsProperties(List.of("https://auth.ayshriv.com"), List.of("GET"), List.of("Authorization"), true, 3600),
                new AppProperties.JwtProperties("issuer", "aud", 15, 7, null, null),
                new AppProperties.AuthProperties(5, 15, true, 10),
                new AppProperties.RateLimitProperties("redis", 30, 10, 120, 60),
                new AppProperties.TokenDenylistProperties(true, 15)
        );
        AppProperties props = new AppProperties(
                sec,
                otp,
                new AppProperties.EmailProperties("no-reply@ayshriv.com", "Ayshriv Auth"),
                new AppProperties.SmsProperties("logging", "IN", List.of("IN"), List.of("+1900"), 5, 20, 5, 100, 500,
                        new AppProperties.TwilioProperties(null, null, null)),
                new AppProperties.GoogleProperties(List.of("cid"), "", false, "http://callback", 60, "http://jwks")
        );

        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.username", "auth_prod_user");
        env.setProperty("spring.datasource.password", "SuperSecretPassword123!");

        ProductionConfigGuard guard = new ProductionConfigGuard(props, env);
        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::validateProductionSettings);
        assertTrue(ex.getMessage().contains("OTP pepper"));
    }

    @Test
    @DisplayName("Should fail when CORS contains localhost in production")
    void shouldFailWhenCorsContainsLocalhost() {
        AppProperties.OtpProperties otp = new AppProperties.OtpProperties(
                "prod-secure-random-pepper-value-with-high-entropy-123456", 5, 5, 60, 5);
        AppProperties.SecurityProperties sec = new AppProperties.SecurityProperties(
                new AppProperties.CorsProperties(List.of("http://localhost:3000"), List.of("GET"), List.of("Authorization"), true, 3600),
                new AppProperties.JwtProperties("issuer", "aud", 15, 7, null, null),
                new AppProperties.AuthProperties(5, 15, true, 10),
                new AppProperties.RateLimitProperties("redis", 30, 10, 120, 60),
                new AppProperties.TokenDenylistProperties(true, 15)
        );
        AppProperties props = new AppProperties(
                sec,
                otp,
                new AppProperties.EmailProperties("no-reply@ayshriv.com", "Ayshriv Auth"),
                new AppProperties.SmsProperties("logging", "IN", List.of("IN"), List.of("+1900"), 5, 20, 5, 100, 500,
                        new AppProperties.TwilioProperties(null, null, null)),
                new AppProperties.GoogleProperties(List.of("cid"), "", false, "http://callback", 60, "http://jwks")
        );

        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.username", "auth_prod_user");
        env.setProperty("spring.datasource.password", "SuperSecretPassword123!");

        ProductionConfigGuard guard = new ProductionConfigGuard(props, env);
        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::validateProductionSettings);
        assertTrue(ex.getMessage().contains("CORS allowed origins contain localhost"));
    }

    @Test
    @DisplayName("Should pass when all production secrets and configurations are valid")
    void shouldPassWhenProductionConfigIsValid() {
        AppProperties.OtpProperties otp = new AppProperties.OtpProperties(
                "prod-secure-random-pepper-value-with-high-entropy-123456", 5, 5, 60, 5);
        AppProperties.SecurityProperties sec = new AppProperties.SecurityProperties(
                new AppProperties.CorsProperties(List.of("https://auth.ayshriv.com"), List.of("GET"), List.of("Authorization"), true, 3600),
                new AppProperties.JwtProperties("issuer", "aud", 15, 7, null, null),
                new AppProperties.AuthProperties(5, 15, true, 10),
                new AppProperties.RateLimitProperties("redis", 30, 10, 120, 60),
                new AppProperties.TokenDenylistProperties(true, 15)
        );
        AppProperties props = new AppProperties(
                sec,
                otp,
                new AppProperties.EmailProperties("no-reply@ayshriv.com", "Ayshriv Auth"),
                new AppProperties.SmsProperties("logging", "IN", List.of("IN"), List.of("+1900"), 5, 20, 5, 100, 500,
                        new AppProperties.TwilioProperties(null, null, null)),
                new AppProperties.GoogleProperties(List.of("cid"), "", false, "http://callback", 60, "http://jwks")
        );

        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.datasource.username", "auth_prod_user");
        env.setProperty("spring.datasource.password", "SuperSecretPassword123!");

        ProductionConfigGuard guard = new ProductionConfigGuard(props, env);
        assertDoesNotThrow(guard::validateProductionSettings);
    }
}
