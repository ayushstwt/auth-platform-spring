package com.ayshriv.authplatform.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtpCryptoUtilsTest {

    @Test
    @DisplayName("Should generate 6-digit numeric string")
    void generate6DigitCode() {
        for (int i = 0; i < 50; i++) {
            String code = OtpCryptoUtils.generate6DigitCode();
            assertThat(code).hasSize(6);
            assertThat(code).containsOnlyDigits();
        }
    }

    @Test
    @DisplayName("Should hash code deterministically with HMAC pepper")
    void hashOtp() {
        String pepper = "secret-pepper-key";
        String dest = "alice@example.com";
        String code = "123456";

        String hash1 = OtpCryptoUtils.hashOtp(code, dest, pepper);
        String hash2 = OtpCryptoUtils.hashOtp(code, dest, pepper);
        String diffHash = OtpCryptoUtils.hashOtp("654321", dest, pepper);

        assertThat(hash1).isNotBlank();
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEqualTo(diffHash);
    }

    @Test
    @DisplayName("Should do constant time comparison correctly")
    void constantTimeEquals() {
        assertThat(OtpCryptoUtils.constantTimeEquals("abcdef", "abcdef")).isTrue();
        assertThat(OtpCryptoUtils.constantTimeEquals("abcdef", "abcdeg")).isFalse();
        assertThat(OtpCryptoUtils.constantTimeEquals("abcdef", null)).isFalse();
        assertThat(OtpCryptoUtils.constantTimeEquals(null, "abcdef")).isFalse();
    }
}
