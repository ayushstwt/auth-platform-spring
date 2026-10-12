package com.ayshriv.authplatform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailChangeConfirmRequest(
        @NotBlank(message = "New email address is required")
        @Email(message = "New email address must be valid")
        String newEmail,

        @NotBlank(message = "Verification code is required")
        @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be 6 digits")
        String code
) {
}
