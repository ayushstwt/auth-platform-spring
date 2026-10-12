package com.ayshriv.authplatform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailChangeRequest(
        @NotBlank(message = "New email address is required")
        @Email(message = "New email address must be valid")
        String newEmail
) {
}
