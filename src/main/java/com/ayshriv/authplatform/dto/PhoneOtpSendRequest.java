package com.ayshriv.authplatform.dto;

import jakarta.validation.constraints.NotBlank;

public record PhoneOtpSendRequest(
        @NotBlank(message = "Phone number is required")
        String phoneNumber
) {
}
