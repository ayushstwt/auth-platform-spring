package com.iosocket.userservice.dto;

public record ApiError(
        String field,
        String message
) {
}
