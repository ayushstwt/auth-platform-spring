package com.ayshriv.authplatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Schema(example = "jane.doe@example.com")
        String email,

        @NotBlank(message = "Password is required")
        @Schema(example = "StrongP@ssw0rd!2026")
        String password
) {
}
