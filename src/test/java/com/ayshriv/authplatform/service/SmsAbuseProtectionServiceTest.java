package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.exception.ForbiddenException;
import com.ayshriv.authplatform.exception.TooManyRequestsException;
import com.ayshriv.authplatform.repository.OtpChallengeRepository;
import com.ayshriv.authplatform.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsAbuseProtectionServiceTest {

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private OtpChallengeRepository otpChallengeRepository;

    private AppProperties appProperties;
    private SmsAbuseProtectionService abuseProtectionService;

    @BeforeEach
    void setUp() {
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties(
                List.of("http://localhost"), List.of("GET"), List.of("*"), true, 3600
        );
        AppProperties.JwtProperties jwt = new AppProperties.JwtProperties(
                "issuer", "aud", 15, 7, "", ""
        );
        AppProperties.AuthProperties auth = new AppProperties.AuthProperties(5, 15, false, 10);
        AppProperties.SecurityProperties sec = new AppProperties.SecurityProperties(cors, jwt, auth);
        AppProperties.OtpProperties otp = new AppProperties.OtpProperties("pepper", 5, 5, 60, 5);
        AppProperties.EmailProperties email = new AppProperties.EmailProperties("test@ayshriv.com", "Auth");
        AppProperties.TwilioProperties twilio = new AppProperties.TwilioProperties("sid", "tok", "+12345");
        AppProperties.SmsProperties sms = new AppProperties.SmsProperties(
                "logging", "IN", List.of("IN", "US"), List.of("+1900", "+1976"), 5, 20, 5, 100, 3, twilio
        );
        AppProperties.GoogleProperties google = new AppProperties.GoogleProperties(List.of("client-1"), null, false, "http://localhost:3000", 60, "https://www.googleapis.com/oauth2/v3/certs");

        appProperties = new AppProperties(sec, otp, email, sms, google);
        abuseProtectionService = new SmsAbuseProtectionService(rateLimiter, otpChallengeRepository, appProperties);
    }

    @Test
    @DisplayName("Should block SMS to premium or fraudulent prefixes")
    void blockPremiumPrefixes() {
        assertThatThrownBy(() -> abuseProtectionService.validateSmsSendAllowed("+19001234567", 1L, "127.0.0.1"))
                .isInstanceOf(ForbiddenException.class);

        assertThatThrownBy(() -> abuseProtectionService.validateSmsSendAllowed("+19761234567", 1L, "127.0.0.1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Should block SMS to countries not in allow-list")
    void blockDisallowedCountries() {
        // +44 is UK, which is not in ["IN", "US"] in this test config
        assertThatThrownBy(() -> abuseProtectionService.validateSmsSendAllowed("+447911123456", 1L, "127.0.0.1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Should block when daily cost guard limit is exceeded")
    void blockWhenDailyCostGuardExceeded() {
        abuseProtectionService.recordSmsSent(); // 1
        abuseProtectionService.recordSmsSent(); // 2
        abuseProtectionService.recordSmsSent(); // 3 (limit is 3)

        assertThatThrownBy(() -> abuseProtectionService.validateSmsSendAllowed("+919876543210", 1L, "127.0.0.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Daily SMS quota exceeded");
    }

    @Test
    @DisplayName("Should pass when all abuse controls are respected")
    void allowValidSms() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(otpChallengeRepository.countByDestinationAndCreatedOnAfter(anyString(), any())).thenReturn(0L);

        abuseProtectionService.validateSmsSendAllowed("+919876543210", 1L, "127.0.0.1");
        abuseProtectionService.recordSmsSent();

        assertThat(abuseProtectionService.getDailySentCount()).isEqualTo(1);
    }
}
