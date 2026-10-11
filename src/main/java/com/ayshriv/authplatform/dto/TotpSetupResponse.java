package com.ayshriv.authplatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TotpSetupResponse(
        @Schema(description = "Base32 encoded secret for manual entry into authenticator app")
        String secret,

        @Schema(description = "Standard otpauth URI for generating QR code")
        String otpauthUri
) {
}
