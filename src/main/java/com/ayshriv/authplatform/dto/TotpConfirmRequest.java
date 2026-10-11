package com.ayshriv.authplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TotpConfirmRequest(
        @NotBlank(message = "TOTP code cannot be blank")
        @Pattern(regexp = "^\\d{6}$", message = "TOTP code must be 6 digits")
        String code
) {
}
