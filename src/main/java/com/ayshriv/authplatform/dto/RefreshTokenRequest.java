package com.ayshriv.authplatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        @Schema(example = "dGhpc2lzYXNhbXBsZXJlZnJlc2h0b2tlbg...")
        String refreshToken
) {
}
