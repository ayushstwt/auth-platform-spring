package com.iosocket.userservice.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull SecurityProperties security
) {
    public record SecurityProperties(
            @NotNull CorsProperties cors
    ) {
    }

    public record CorsProperties(
            @NotEmpty List<String> allowedOrigins,
            @NotEmpty List<String> allowedMethods,
            @NotEmpty List<String> allowedHeaders,
            boolean allowCredentials,
            long maxAgeSeconds
    ) {
    }
}
