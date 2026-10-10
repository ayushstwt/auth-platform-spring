package com.ayshriv.authplatform.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Unauthenticated request to protected endpoint should return 401 envelope")
    void protectedEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/protected/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E004")))
                .andExpect(jsonPath("$.message", is("Authentication required")))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("Public health endpoint should return 200 with success envelope")
    void publicHealthEndpointReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.status", is("UP")))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    @DisplayName("Actuator health endpoint should be publicly accessible")
    void actuatorHealthReturns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    @DisplayName("Custom X-Request-Id header should be preserved and returned")
    void customCorrelationIdPreserved() throws Exception {
        String customId = "req-custom-uuid-12345";
        mockMvc.perform(get("/api/v1/health")
                        .header("X-Request-Id", customId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", customId));
    }

    @Test
    @DisplayName("Response should include security headers")
    void securityHeadersPresent() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }
}
