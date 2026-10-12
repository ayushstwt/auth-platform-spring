package com.ayshriv.authplatform.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleTokenLoginRequest(
        @NotBlank(message = "idToken must not be blank")
        String idToken
) {
}
