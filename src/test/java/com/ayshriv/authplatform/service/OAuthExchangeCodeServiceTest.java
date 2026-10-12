package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthExchangeCodeServiceTest {

    private OAuthExchangeCodeService exchangeCodeService;

    @BeforeEach
    void setUp() {
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties(List.of("*"), List.of("GET"), List.of("*"), false, 3600);
        AppProperties.JwtProperties jwt = new AppProperties.JwtProperties("iss", "aud", 15, 7, "", "");
        AppProperties.AuthProperties auth = new AppProperties.AuthProperties(5, 15, false, 10);
        AppProperties.SecurityProperties sec = new AppProperties.SecurityProperties(cors, jwt, auth);
        AppProperties.OtpProperties otp = new AppProperties.OtpProperties("pepper", 5, 5, 60, 5);
        AppProperties.EmailProperties email = new AppProperties.EmailProperties("test@ayshriv.com", "Auth");
        AppProperties.TwilioProperties twilio = new AppProperties.TwilioProperties("sid", "tok", "+1");
        AppProperties.SmsProperties sms = new AppProperties.SmsProperties("logging", "IN", List.of("IN"), List.of(), 5, 20, 5, 100, 50, twilio);
        AppProperties.GoogleProperties google = new AppProperties.GoogleProperties(List.of("client"), null, true, "http://localhost:3000", 60, "https://www.googleapis.com/oauth2/v3/certs");

        AppProperties appProperties = new AppProperties(sec, otp, email, sms, google);
        exchangeCodeService = new OAuthExchangeCodeService(appProperties);
    }

    @Test
    @DisplayName("Should generate single-use exchange code and consume once")
    void generateAndConsumeCode_Success() {
        String code = exchangeCodeService.generateCode(101L);
        assertThat(code).isNotBlank();

        Long userId = exchangeCodeService.consumeCode(code);
        assertThat(userId).isEqualTo(101L);

        // Second consumption must fail (single-use)
        assertThatThrownBy(() -> exchangeCodeService.consumeCode(code))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("Should reject invalid or null exchange code")
    void consumeInvalidCode() {
        assertThatThrownBy(() -> exchangeCodeService.consumeCode("invalid-code"))
                .isInstanceOf(UnauthorizedException.class);

        assertThatThrownBy(() -> exchangeCodeService.consumeCode(null))
                .isInstanceOf(UnauthorizedException.class);
    }
}
