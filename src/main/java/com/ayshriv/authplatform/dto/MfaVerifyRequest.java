package com.ayshriv.authplatform.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaVerifyRequest(
        @NotBlank(message = "MFA token cannot be blank")
        String mfaToken,

        @NotBlank(message = "MFA factor cannot be blank")
        String factor, // "TOTP", "EMAIL", "SMS", "RECOVERY_CODE"

        @NotBlank(message = "Verification code cannot be blank")
        String code
) {
}
