package com.ayshriv.authplatform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailOtpSendRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a well-formed email address")
        String email
) {
}
