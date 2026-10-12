package com.ayshriv.authplatform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ayshriv.authplatform.dto.ChangePasswordRequest;
import com.ayshriv.authplatform.dto.LoginRequest;
import com.ayshriv.authplatform.dto.RefreshTokenRequest;
import com.ayshriv.authplatform.dto.RegisterRequest;
import com.ayshriv.authplatform.dto.UpdateUserRequest;
import com.ayshriv.authplatform.repository.RefreshTokenRepository;
import com.ayshriv.authplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String accessToken;
    private String refreshToken;

    @BeforeEach
    void setUp() throws Exception {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        RegisterRequest registerReq = new RegisterRequest("profile.user@example.com", "myStrongPassword2026!", "Profile", "User");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("profile.user@example.com", "myStrongPassword2026!"))))
                .andExpect(status().isOk())
                .andReturn();

        String body = loginResult.getResponse().getContentAsString();
        this.accessToken = objectMapper.readTree(body).path("data").path("accessToken").asText();
        this.refreshToken = objectMapper.readTree(body).path("data").path("refreshToken").asText();
    }

    @Test
    @DisplayName("GET /api/v1/users/me should return authenticated user details")
    void getProfileSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.email", is("profile.user@example.com")))
                .andExpect(jsonPath("$.data.firstName", is("Profile")))
                .andExpect(jsonPath("$.data.lastName", is("User")));
    }

    @Test
    @DisplayName("GET /api/v1/users/me without token should return 401 Unauthorized")
    void getProfileUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E004")));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me should update user first and last name")
    void updateProfileSuccess() throws Exception {
        UpdateUserRequest updateReq = new UpdateUserRequest("UpdatedFirst", "UpdatedLast");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.firstName", is("UpdatedFirst")))
                .andExpect(jsonPath("$.data.lastName", is("UpdatedLast")));
    }

    @Test
    @DisplayName("POST /api/v1/users/me/change-password should update password and revoke other sessions")
    void changePasswordSuccess() throws Exception {
        ChangePasswordRequest changeReq = new ChangePasswordRequest("myStrongPassword2026!", "brandNewPassword2026!");

        mockMvc.perform(post("/api/v1/users/me/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        // Old refresh token must be revoked
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized());

        // Login with new password must succeed
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("profile.user@example.com", "brandNewPassword2026!"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout-all should revoke all user sessions")
    void logoutAllSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        // Refresh token should no longer work
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }
}
