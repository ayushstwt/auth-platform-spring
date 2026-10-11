package com.ayshriv.authplatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DisableMfaRequest(
        @Schema(description = "Password (if user has password) or TOTP/MFA code")
        String password,

        @Schema(description = "TOTP or OTP code if disabling via code")
        String code
) {
}
