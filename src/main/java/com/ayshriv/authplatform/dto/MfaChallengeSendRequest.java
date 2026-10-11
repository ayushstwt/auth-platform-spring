package com.ayshriv.authplatform.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaChallengeSendRequest(
        @NotBlank(message = "MFA token cannot be blank")
        String mfaToken,

        @NotBlank(message = "Channel must be specified (EMAIL or SMS)")
        String channel
) {
}
