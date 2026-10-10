package com.iosocket.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        @Schema(example = "OldP@ssw0rd!2026")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 12, max = 128, message = "New password must be between 12 and 128 characters")
        @Schema(example = "NewStrongP@ssw0rd!2026")
        String newPassword
) {
}
