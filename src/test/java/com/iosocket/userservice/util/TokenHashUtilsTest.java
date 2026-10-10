package com.iosocket.userservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHashUtilsTest {

    @Test
    @DisplayName("Should generate random secure token with sufficient entropy")
    void generateSecureToken() {
        String token1 = TokenHashUtils.generateSecureToken();
        String token2 = TokenHashUtils.generateSecureToken();

        assertThat(token1).isNotBlank();
        assertThat(token2).isNotBlank();
        assertThat(token1).isNotEqualTo(token2);
        assertThat(token1.length()).isGreaterThanOrEqualTo(40);
    }

    @Test
    @DisplayName("Should generate deterministic SHA-256 hash in hex format")
    void hashToken() {
        String raw = "sample-token-12345";
        String hash1 = TokenHashUtils.hashToken(raw);
        String hash2 = TokenHashUtils.hashToken(raw);

        assertThat(hash1).isNotNull();
        assertThat(hash1).hasSize(64);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Constant-time comparison should correctly compare matching and non-matching strings")
    void constantTimeEquals() {
        assertThat(TokenHashUtils.constantTimeEquals("secret123", "secret123")).isTrue();
        assertThat(TokenHashUtils.constantTimeEquals("secret123", "secret456")).isFalse();
        assertThat(TokenHashUtils.constantTimeEquals(null, "secret123")).isFalse();
    }
}
