package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.exception.UnauthorizedException;
import com.ayshriv.authplatform.security.RsaKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaTokenServiceTest {

    private MfaTokenService mfaTokenService;

    @BeforeEach
    void setUp() {
        RsaKeyProvider rsaKeyProvider = new RsaKeyProvider(null);
        rsaKeyProvider.init();
        AppProperties.SecurityProperties securityProps = new AppProperties.SecurityProperties(
                null,
                new AppProperties.JwtProperties("test-issuer", "test-audience", 15, 7, "", ""),
                null
        );

        AppProperties.MfaProperties mfaProps = new AppProperties.MfaProperties(
                "k1",
                Map.of("k1", "01234567890123456789012345678901"),
                5,
                3,
                15,
                false,
                "AuthPlatform"
        );
        AppProperties appProperties = new AppProperties(securityProps, null, null, null, null, mfaProps);

        this.mfaTokenService = new MfaTokenService(rsaKeyProvider, appProperties);
    }

    @Test
    @DisplayName("Should create and successfully validate valid MFA intermediate token")
    void createAndValidateToken() {
        String token = mfaTokenService.createMfaToken(42L, "pwd");

        assertThat(token).isNotBlank();
        var payload = mfaTokenService.validateMfaToken(token);

        assertThat(payload.userId()).isEqualTo(42L);
        assertThat(payload.initialFactor()).isEqualTo("pwd");
        assertThat(payload.tokenId()).isNotBlank();
    }

    @Test
    @DisplayName("Should lock and reject MFA token when maximum failed attempts are exceeded")
    void lockTokenOnExceededAttempts() {
        String token = mfaTokenService.createMfaToken(42L, "pwd");
        var payload = mfaTokenService.validateMfaToken(token);

        // Record 3 failed attempts (max is 3)
        mfaTokenService.recordFailedAttempt(payload.tokenId());
        mfaTokenService.recordFailedAttempt(payload.tokenId());
        mfaTokenService.recordFailedAttempt(payload.tokenId());

        assertThatThrownBy(() -> mfaTokenService.validateMfaToken(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("maximum failed attempts reached");
    }

    @Test
    @DisplayName("Should reject forged or tampered MFA token")
    void rejectForgedMfaToken() {
        assertThatThrownBy(() -> mfaTokenService.validateMfaToken("eyJhbGciOiJIUzI1NiJ9.e30.bogus"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
