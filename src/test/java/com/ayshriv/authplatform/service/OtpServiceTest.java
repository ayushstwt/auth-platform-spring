package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.entity.OtpChallenge;
import com.ayshriv.authplatform.entity.OtpChannel;
import com.ayshriv.authplatform.entity.OtpPurpose;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.exception.BadRequestException;
import com.ayshriv.authplatform.exception.TooManyRequestsException;
import com.ayshriv.authplatform.repository.OtpChallengeRepository;
import com.ayshriv.authplatform.security.RateLimiter;
import com.ayshriv.authplatform.util.OtpCryptoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpChallengeRepository otpChallengeRepository;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private AuditService auditService;

    private AppProperties appProperties;
    private OtpService otpService;

    private static final String PEPPER = "secure-test-pepper-1234567890-test";
    private static final String DESTINATION = "user@example.com";

    @BeforeEach
    void setUp() {
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties(
                List.of("http://localhost"), List.of("GET"), List.of("*"), true, 3600
        );
        AppProperties.JwtProperties jwt = new AppProperties.JwtProperties(
                "issuer", "aud", 15, 7, "", ""
        );
        AppProperties.AuthProperties auth = new AppProperties.AuthProperties(5, 15, false);
        AppProperties.SecurityProperties sec = new AppProperties.SecurityProperties(cors, jwt, auth);
        AppProperties.OtpProperties otp = new AppProperties.OtpProperties(PEPPER, 5, 5, 60, 5);
        AppProperties.EmailProperties email = new AppProperties.EmailProperties("no-reply@ayshriv.com", "Ayshriv");

        appProperties = new AppProperties(sec, otp, email);
        otpService = new OtpService(otpChallengeRepository, rateLimiter, auditService, appProperties);
    }

    @Test
    @DisplayName("Should generate 6-digit code and save challenge with HMAC hash")
    void generateAndSaveOtp_Success() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(otpChallengeRepository.countByDestinationAndCreatedOnAfter(eq(DESTINATION), any())).thenReturn(0L);
        when(otpChallengeRepository.findTopByDestinationAndPurposeAndActiveTrueAndDeletedFalseOrderByCreatedOnDesc(
                DESTINATION, OtpPurpose.EMAIL_VERIFY)).thenReturn(Optional.empty());

        User user = new User();
        user.setId(1L);
        user.setEmail(DESTINATION);

        String code = otpService.generateAndSaveOtp(user, OtpChannel.EMAIL, OtpPurpose.EMAIL_VERIFY, DESTINATION, "127.0.0.1");

        assertThat(code).hasSize(6);
        assertThat(code).containsOnlyDigits();
        verify(otpChallengeRepository).save(any(OtpChallenge.class));
    }

    @Test
    @DisplayName("Should block code generation when IP rate limit exceeded")
    void generateAndSaveOtp_IpRateLimited() {
        when(rateLimiter.tryAcquire("ip:127.0.0.1")).thenReturn(false);

        assertThatThrownBy(() -> otpService.generateAndSaveOtp(
                null, OtpChannel.EMAIL, OtpPurpose.LOGIN, DESTINATION, "127.0.0.1"
        )).isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("IP address");

        verify(otpChallengeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should block code generation when destination rate limit exceeded")
    void generateAndSaveOtp_DestinationRateLimited() {
        when(rateLimiter.tryAcquire("ip:127.0.0.1")).thenReturn(true);
        when(rateLimiter.tryAcquire("dest:" + DESTINATION)).thenReturn(false);

        assertThatThrownBy(() -> otpService.generateAndSaveOtp(
                null, OtpChannel.EMAIL, OtpPurpose.LOGIN, DESTINATION, "127.0.0.1"
        )).isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("destination");

        verify(otpChallengeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should enforce resend cooldown if requested within cooldown period")
    void generateAndSaveOtp_CooldownActive() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(otpChallengeRepository.countByDestinationAndCreatedOnAfter(eq(DESTINATION), any())).thenReturn(0L);

        OtpChallenge existing = new OtpChallenge();
        existing.setCreatedOn(Instant.now().minus(20, ChronoUnit.SECONDS));

        when(otpChallengeRepository.findTopByDestinationAndPurposeAndActiveTrueAndDeletedFalseOrderByCreatedOnDesc(
                DESTINATION, OtpPurpose.LOGIN)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> otpService.generateAndSaveOtp(
                null, OtpChannel.EMAIL, OtpPurpose.LOGIN, DESTINATION, "127.0.0.1"
        )).isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("seconds");

        verify(otpChallengeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should verify valid code successfully and consume challenge")
    void verifyOtp_Success() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);

        String plainCode = "123456";
        String hashedCode = OtpCryptoUtils.hashOtp(plainCode, DESTINATION, PEPPER);

        OtpChallenge challenge = new OtpChallenge(
                null, OtpChannel.EMAIL, OtpPurpose.EMAIL_VERIFY, DESTINATION,
                hashedCode, Instant.now().plus(5, ChronoUnit.MINUTES), 5, "127.0.0.1"
        );

        when(otpChallengeRepository.findTopByDestinationAndPurposeAndActiveTrueAndDeletedFalseOrderByCreatedOnDesc(
                DESTINATION, OtpPurpose.EMAIL_VERIFY)).thenReturn(Optional.of(challenge));

        OtpChallenge verified = otpService.verifyOtp(DESTINATION, OtpPurpose.EMAIL_VERIFY, plainCode, "127.0.0.1");

        assertThat(verified.isConsumed()).isTrue();
        assertThat(verified.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should reject verification when code is expired")
    void verifyOtp_Expired() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);

        String hashedCode = OtpCryptoUtils.hashOtp("123456", DESTINATION, PEPPER);
        OtpChallenge challenge = new OtpChallenge(
                null, OtpChannel.EMAIL, OtpPurpose.EMAIL_VERIFY, DESTINATION,
                hashedCode, Instant.now().minus(1, ChronoUnit.MINUTES), 5, "127.0.0.1"
        );

        when(otpChallengeRepository.findTopByDestinationAndPurposeAndActiveTrueAndDeletedFalseOrderByCreatedOnDesc(
                DESTINATION, OtpPurpose.EMAIL_VERIFY)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> otpService.verifyOtp(DESTINATION, OtpPurpose.EMAIL_VERIFY, "123456", "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Should reject and increment attempt on wrong code; exhaust on max attempts")
    void verifyOtp_WrongCode_AndExhaustAttempts() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);

        String actualCode = "123456";
        String hashedCode = OtpCryptoUtils.hashOtp(actualCode, DESTINATION, PEPPER);

        OtpChallenge challenge = new OtpChallenge(
                null, OtpChannel.EMAIL, OtpPurpose.LOGIN, DESTINATION,
                hashedCode, Instant.now().plus(5, ChronoUnit.MINUTES), 5, "127.0.0.1"
        );
        challenge.setAttempts(4); // 4th attempt already used, 5th will exhaust

        when(otpChallengeRepository.findTopByDestinationAndPurposeAndActiveTrueAndDeletedFalseOrderByCreatedOnDesc(
                DESTINATION, OtpPurpose.LOGIN)).thenReturn(Optional.of(challenge));

        // Wrong code on 5th attempt -> exhausted
        assertThatThrownBy(() -> otpService.verifyOtp(DESTINATION, OtpPurpose.LOGIN, "999999", "127.0.0.1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Maximum attempts reached");

        assertThat(challenge.getAttempts()).isEqualTo(5);
        assertThat(challenge.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should reject when no active challenge exists for destination and purpose")
    void verifyOtp_NoChallenge() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);

        when(otpChallengeRepository.findTopByDestinationAndPurposeAndActiveTrueAndDeletedFalseOrderByCreatedOnDesc(
                DESTINATION, OtpPurpose.PASSWORD_RESET)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verifyOtp(DESTINATION, OtpPurpose.PASSWORD_RESET, "123456", "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
    }
}
