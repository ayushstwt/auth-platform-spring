package com.iosocket.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iosocket.userservice.dto.AuthResponse;
import com.iosocket.userservice.dto.LoginRequest;
import com.iosocket.userservice.dto.LogoutRequest;
import com.iosocket.userservice.dto.RefreshTokenRequest;
import com.iosocket.userservice.dto.RegisterRequest;
import com.iosocket.userservice.entity.RefreshToken;
import com.iosocket.userservice.entity.User;
import com.iosocket.userservice.entity.UserStatus;
import com.iosocket.userservice.repository.RefreshTokenRepository;
import com.iosocket.userservice.repository.UserRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should successfully register a new user and return 201 Created")
    void registerSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "john.doe@example.com",
                "securePassword123!",
                "John",
                "Doe"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.email", is("john.doe@example.com")))
                .andExpect(jsonPath("$.data.firstName", is("John")))
                .andExpect(jsonPath("$.data.lastName", is("Doe")))
                .andExpect(jsonPath("$.data.emailVerified", is(false)))
                .andExpect(header().exists("Location"));

        User persisted = userRepository.findByEmail("john.doe@example.com").orElseThrow();
        assertThat(persisted.getPasswordHash()).isNotEqualTo("securePassword123!");
    }

    @Test
    @DisplayName("Should reject registration with duplicate email and return 409 Conflict")
    void registerDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "john.duplicate@example.com",
                "securePassword123!",
                "John",
                "Duplicate"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E003")));
    }

    @Test
    @DisplayName("Should reject registration with weak or short password")
    void registerWeakPassword() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "john.weak@example.com",
                "short",
                "John",
                "Weak"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E005")));
    }

    @Test
    @DisplayName("Should authenticate user and return access & refresh tokens on login")
    void loginSuccess() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("login.user@example.com", "myStrongPassword2026!", "Login", "User");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("login.user@example.com", "myStrongPassword2026!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.data.user.email", is("login.user@example.com")));
    }

    @Test
    @DisplayName("Should return generic 401 error on wrong password")
    void loginWrongPassword() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("wrong.pass@example.com", "myStrongPassword2026!", "Wrong", "Pass");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest loginReq = new LoginRequest("wrong.pass@example.com", "incorrectPassword!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E012")))
                .andExpect(jsonPath("$.message", is("Invalid credentials")));
    }

    @Test
    @DisplayName("Should lock account after 5 consecutive failed login attempts")
    void accountLockoutAfter5FailedAttempts() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("lockout.test@example.com", "myStrongPassword2026!", "Lock", "Test");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        LoginRequest badLogin = new LoginRequest("lockout.test@example.com", "wrongPassword!");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badLogin)))
                    .andExpect(status().isUnauthorized());
        }

        User lockedUser = userRepository.findByEmail("lockout.test@example.com").orElseThrow();
        assertThat(lockedUser.isAccountLocked()).isTrue();
        assertThat(lockedUser.getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(lockedUser.getLockedUntil()).isNotNull();

        // Even with right password, login still fails while locked
        LoginRequest correctLogin = new LoginRequest("lockout.test@example.com", "myStrongPassword2026!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("E012")));
    }

    @Test
    @DisplayName("Should rotate refresh token on /refresh and issue new tokens")
    void refreshTokenRotation() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("refresh.test@example.com", "myStrongPassword2026!", "Refresh", "User");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("refresh.test@example.com", "myStrongPassword2026!"))))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String originalRefreshToken = objectMapper.readTree(responseBody).path("data").path("refreshToken").asText();

        // Perform refresh
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(originalRefreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andReturn();

        String newResponseBody = refreshResult.getResponse().getContentAsString();
        String newRefreshToken = objectMapper.readTree(newResponseBody).path("data").path("refreshToken").asText();

        assertThat(newRefreshToken).isNotEqualTo(originalRefreshToken);
    }

    @Test
    @DisplayName("Should detect refresh token reuse and revoke the entire token family")
    void refreshTokenReuseDetection() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("reuse.test@example.com", "myStrongPassword2026!", "Reuse", "User");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("reuse.test@example.com", "myStrongPassword2026!"))))
                .andExpect(status().isOk())
                .andReturn();

        String originalRefreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        // 1st Refresh: succeeds and rotates token
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(originalRefreshToken))))
                .andExpect(status().isOk())
                .andReturn();

        String newRefreshToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        // 2nd Refresh with the OLD already-used token: REUSE DETECTED!
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(originalRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("E013")));

        // Because the family was revoked, the new token should NOW ALSO FAIL
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(newRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("E013")));
    }

    @Test
    @DisplayName("Should revoke token on logout and reject subsequent refresh")
    void logoutRevokesTokenFamily() throws Exception {
        RegisterRequest registerReq = new RegisterRequest("logout.test@example.com", "myStrongPassword2026!", "Logout", "User");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("logout.test@example.com", "myStrongPassword2026!"))))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(refreshToken))))
                .andExpect(status().isOk());

        // Refresh with revoked token should fail
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }
}
