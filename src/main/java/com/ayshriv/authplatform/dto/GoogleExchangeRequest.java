package com.ayshriv.authplatform.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleExchangeRequest(
        @NotBlank(message = "exchangeCode must not be blank")
        String exchangeCode
) {
}
