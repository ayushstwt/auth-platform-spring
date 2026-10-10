package com.ayshriv.authplatform.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskingUtilsTest {

    @Test
    @DisplayName("Should mask email addresses safely")
    void shouldMaskEmails() {
        assertThat(LogMaskingUtils.maskEmail("john.doe@example.com")).isEqualTo("j***e@example.com");
        assertThat(LogMaskingUtils.maskEmail("a@example.com")).isEqualTo("***@example.com");
        assertThat(LogMaskingUtils.maskEmail("")).isEqualTo("[EMPTY]");
        assertThat(LogMaskingUtils.maskEmail(null)).isEqualTo("[EMPTY]");
    }

    @Test
    @DisplayName("Should mask phone numbers safely")
    void shouldMaskPhones() {
        assertThat(LogMaskingUtils.maskPhone("+1-555-123-4567")).isEqualTo("*******4567");
        assertThat(LogMaskingUtils.maskPhone("123")).isEqualTo("****");
        assertThat(LogMaskingUtils.maskPhone("")).isEqualTo("[EMPTY]");
        assertThat(LogMaskingUtils.maskPhone(null)).isEqualTo("[EMPTY]");
    }

    @Test
    @DisplayName("Should mask tokens safely")
    void shouldMaskTokens() {
        assertThat(LogMaskingUtils.maskToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9")).startsWith("eyJh...").endsWith("CJ9");
        assertThat(LogMaskingUtils.maskToken("short")).isEqualTo("[REDACTED]");
        assertThat(LogMaskingUtils.maskToken("")).isEqualTo("[EMPTY]");
        assertThat(LogMaskingUtils.maskToken(null)).isEqualTo("[EMPTY]");
    }
}
