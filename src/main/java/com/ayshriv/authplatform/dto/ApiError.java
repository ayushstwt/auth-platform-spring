package com.ayshriv.authplatform.dto;

public record ApiError(
        String field,
        String message
) {
}
