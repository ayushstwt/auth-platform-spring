package com.ayshriv.authplatform.security;

import com.ayshriv.authplatform.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @Test
    @DisplayName("Should accept valid password with 12+ characters")
    void acceptValidPassword() {
        assertThatCode(() -> validator.validate("correct horse battery staple 2026"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject password shorter than 12 characters")
    void rejectShortPassword() {
        assertThatThrownBy(() -> validator.validate("short_pass1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least 12 characters");
    }

    @Test
    @DisplayName("Should reject password longer than 128 characters")
    void rejectLongPassword() {
        String longPass = "a".repeat(129);
        assertThatThrownBy(() -> validator.validate(longPass))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot exceed 128 characters");
    }

    @Test
    @DisplayName("Should reject common breached passwords")
    void rejectCommonPassword() {
        assertThatThrownBy(() -> validator.validate("password123456"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("too common");
    }
}
