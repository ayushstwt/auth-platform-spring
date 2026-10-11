package com.ayshriv.authplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PhoneVerifyRequest(
        @NotBlank(message = "Phone number is required")
        String phoneNumber,

        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be 6 digits")
        String code
) {
}
