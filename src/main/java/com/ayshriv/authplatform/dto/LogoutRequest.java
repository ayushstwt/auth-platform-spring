package com.ayshriv.authplatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LogoutRequest(
        @Schema(example = "dGhpc2lzYXNhbXBsZXJlZnJlc2h0b2tlbg...")
        String refreshToken
) {
}
