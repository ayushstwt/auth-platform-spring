package com.ayshriv.authplatform.controller;

import com.ayshriv.authplatform.dto.EmailChangeConfirmRequest;
import com.ayshriv.authplatform.dto.EmailChangeRequest;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.entity.UserStatus;
import com.ayshriv.authplatform.repository.UserRepository;
import com.ayshriv.authplatform.security.JwtTokenService;
import com.ayshriv.authplatform.service.TestEmailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountLifecycleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private TestEmailSender testEmailSender;

    private User testUser;
    private String userToken;

    @BeforeEach
    void setUp() {
        userRepository.hardDeleteAll();
        testEmailSender.clear();

        testUser = new User();
        testUser.setEmail("lifecycle." + System.currentTimeMillis() + "@example.com");
        testUser.setPasswordHash("hashedPassword123");
        testUser.setFirstName("Lifecycle");
        testUser.setLastName("User");
        testUser.setStatus(UserStatus.ACTIVE);
        testUser = userRepository.save(testUser);

        userToken = jwtTokenService.generateAccessToken(testUser);
    }

    @Test
    @DisplayName("Email change flow: request OTP to new email and confirm email change")
    void emailChangeFlowSuccess() throws Exception {
        EmailChangeRequest changeRequest = new EmailChangeRequest("new.email@example.com");

        mockMvc.perform(post("/api/v1/users/me/email/request")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        assertEquals("new.email@example.com", testEmailSender.getLastRecipient());
        String otpCode = testEmailSender.getLastCode();

        EmailChangeConfirmRequest confirmRequest = new EmailChangeConfirmRequest("new.email@example.com", otpCode);

        mockMvc.perform(post("/api/v1/users/me/email/confirm")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.email", is("new.email@example.com")));
    }

    @Test
    @DisplayName("Export user personal data via GET /api/v1/users/me/export")
    void exportUserDataSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/export")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.email", is(testUser.getEmail())))
                .andExpect(jsonPath("$.data.firstName", is("Lifecycle")));
    }

    @Test
    @DisplayName("Soft-delete user account via DELETE /api/v1/users/me")
    void softDeleteAccountSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        // Verified through repository direct query that user is marked deleted
        User userInDb = userRepository.findById(testUser.getId()).orElse(null);
        // BaseEntity @SQLRestriction("IS_DELETED = false") means findById returns empty!
        org.junit.jupiter.api.Assertions.assertNull(userInDb, "Soft-deleted user should be filtered out by SQLRestriction");
    }
}
