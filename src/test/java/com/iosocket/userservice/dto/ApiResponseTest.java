package com.iosocket.userservice.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iosocket.userservice.config.JacksonConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    @Test
    @DisplayName("Should serialize success response envelope correctly")
    void shouldSerializeSuccessEnvelope() throws Exception {
        ApiResponse<Map<String, String>> response = ApiResponse.success("Operation successful", Map.of("key", "value"));

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"status\":\"success\"");
        assertThat(json).contains("\"message\":\"Operation successful\"");
        assertThat(json).contains("\"data\":{\"key\":\"value\"}");
        assertThat(json).contains("\"timestamp\":");
        assertThat(json).doesNotContain("\"errorCode\"");
        assertThat(json).doesNotContain("\"errors\"");
        assertThat(json).doesNotContain("\"metadata\"");
    }

    @Test
    @DisplayName("Should serialize paginated success response envelope with metadata")
    void shouldSerializePaginatedEnvelope() throws Exception {
        PageMetadata metadata = PageMetadata.from(new PageImpl<>(List.of("item1", "item2"), PageRequest.of(0, 10), 2));
        ApiResponse<List<String>> response = ApiResponse.success("Fetched items", List.of("item1", "item2"), metadata);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"status\":\"success\"");
        assertThat(json).contains("\"data\":[\"item1\",\"item2\"]");
        assertThat(json).contains("\"metadata\":{\"currentPage\":0,\"pageSize\":10,\"totalPages\":1,\"totalItems\":2}");
    }

    @Test
    @DisplayName("Should serialize error response envelope with errorCode and path")
    void shouldSerializeErrorEnvelope() throws Exception {
        ApiResponse<Void> response = ApiResponse.error("E001", "Resource not found", "/api/v1/test/1");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"status\":\"error\"");
        assertThat(json).contains("\"errorCode\":\"E001\"");
        assertThat(json).contains("\"message\":\"Resource not found\"");
        assertThat(json).contains("\"data\":null");
        assertThat(json).contains("\"path\":\"/api/v1/test/1\"");
    }

    @Test
    @DisplayName("Should serialize error response envelope with field validation errors")
    void shouldSerializeValidationErrorEnvelope() throws Exception {
        List<ApiError> errors = List.of(
                new ApiError("email", "must be a well-formed email address"),
                new ApiError("password", "must not be blank")
        );
        ApiResponse<Void> response = ApiResponse.error("E005", "Validation failed", errors, "/api/v1/register");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"status\":\"error\"");
        assertThat(json).contains("\"errorCode\":\"E005\"");
        assertThat(json).contains("\"message\":\"Validation failed\"");
        assertThat(json).contains("\"errors\":[");
        assertThat(json).contains("\"field\":\"email\"");
        assertThat(json).contains("\"field\":\"password\"");
    }
}
