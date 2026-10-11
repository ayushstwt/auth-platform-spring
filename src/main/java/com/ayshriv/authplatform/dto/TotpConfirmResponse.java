package com.ayshriv.authplatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record TotpConfirmResponse(
        @Schema(description = "10 single-use recovery codes, shown only once")
        List<String> recoveryCodes
) {
}
