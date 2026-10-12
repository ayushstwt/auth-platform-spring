package com.ayshriv.authplatform.util;

import com.ayshriv.authplatform.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberValidationUtilsTest {

    @Test
    @DisplayName("Should normalize valid Indian mobile numbers to E.164")
    void normalizeIndianNumbers() {
        String e164 = PhoneNumberValidationUtils.normalizeToE164("9876543210", "IN");
        assertThat(e164).isEqualTo("+919876543210");

        String e164WithPrefix = PhoneNumberValidationUtils.normalizeToE164("+91 98765 43210", "IN");
        assertThat(e164WithPrefix).isEqualTo("+919876543210");
    }

    @Test
    @DisplayName("Should normalize valid US mobile numbers to E.164")
    void normalizeUsNumbers() {
        String e164 = PhoneNumberValidationUtils.normalizeToE164("+1 (415) 555-2671", "US");
        assertThat(e164).isEqualTo("+14155552671");
    }

    @Test
    @DisplayName("Should reject invalid or garbled phone numbers")
    void rejectInvalidNumbers() {
        assertThatThrownBy(() -> PhoneNumberValidationUtils.normalizeToE164("12345", "IN"))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> PhoneNumberValidationUtils.normalizeToE164("invalid-phone", "IN"))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> PhoneNumberValidationUtils.normalizeToE164("", "IN"))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> PhoneNumberValidationUtils.normalizeToE164(null, "IN"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Should correctly identify region code from E.164 number")
    void identifyRegionCode() {
        assertThat(PhoneNumberValidationUtils.getRegionCodeForNumber("+919876543210")).isEqualTo("IN");
        assertThat(PhoneNumberValidationUtils.getRegionCodeForNumber("+14155552671")).isEqualTo("US");
        assertThat(PhoneNumberValidationUtils.getRegionCodeForNumber("+447700900123")).isEqualTo("GB");
    }
}
