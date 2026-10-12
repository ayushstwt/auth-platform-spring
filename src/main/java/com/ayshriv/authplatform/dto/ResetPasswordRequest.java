package com.ayshriv.authplatform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a well-formed email address")
        String email,

        @NotBlank(message = "Code is required")
        @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be 6 digits")
        String code,

        @NotBlank(message = "New password is required")
        @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
        String newPassword
) {
}
