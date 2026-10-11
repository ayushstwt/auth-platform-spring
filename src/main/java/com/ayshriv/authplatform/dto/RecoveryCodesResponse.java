package com.ayshriv.authplatform.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record RecoveryCodesResponse(
        @Schema(description = "New set of 10 single-use recovery codes")
        List<String> recoveryCodes
) {
}
