package com.iosocket.userservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

public record ApiResponse<T>(
        String status,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorCode,
        T data,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object metadata,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ApiError> errors,
        @JsonInclude(JsonInclude.Include.NON_NULL) String path,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("success", message, null, data, null, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> success(String message, T data, Object metadata) {
        return new ApiResponse<>("success", message, null, data, metadata, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String errorCode, String message, String path) {
        return new ApiResponse<>("error", message, errorCode, null, null, null, path, Instant.now());
    }

    public static <T> ApiResponse<T> error(String errorCode, String message, List<ApiError> errors, String path) {
        return new ApiResponse<>("error", message, errorCode, null, null, errors, path, Instant.now());
    }
}
