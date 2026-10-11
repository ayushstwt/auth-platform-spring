package com.ayshriv.authplatform.controller;

import com.ayshriv.authplatform.dto.*;
import com.ayshriv.authplatform.entity.Role;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.entity.UserStatus;
import com.ayshriv.authplatform.repository.RoleRepository;
import com.ayshriv.authplatform.repository.UserRepository;
import com.ayshriv.authplatform.security.JwtTokenService;
import com.ayshriv.authplatform.service.AesGcmEncryptionService;
import com.ayshriv.authplatform.service.TotpService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MfaFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private TotpService totpService;

    @Autowired
    private AesGcmEncryptionService aesGcmEncryptionService;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private String userAccessToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        Role userRole = roleRepository.findByName(Role.ROLE_USER)
                .orElseGet(() -> roleRepository.save(new Role(Role.ROLE_USER)));

        User user = new User();
        user.setEmail("mfa-user@example.com");
        user.setPasswordHash(passwordEncoder.encode("SecurePass123!"));
        user.setFirstName("MFA");
        user.setLastName("Tester");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setPhoneE164("+919876543210");
        user.setPhoneVerified(true);
        user.setRoles(Set.of(userRole));
        user.setMfaEnabled(false);

        this.testUser = userRepository.save(user);
        this.userAccessToken = jwtTokenService.generateAccessToken(testUser);
    }

    @Test
    @DisplayName("Complete MFA lifecycle: setup TOTP, confirm TOTP, login with password requiring MFA, verify TOTP to obtain tokens")
    void completeMfaTotpLifecycle() throws Exception {
        // 1. Setup TOTP: POST /api/v1/users/me/mfa/totp/setup
        MvcResult setupResult = mockMvc.perform(post("/api/v1/users/me/mfa/totp/setup")
                        .header("Authorization", "Bearer " + userAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.secret").exists())
                .andExpect(jsonPath("$.data.otpauthUri").exists())
                .andReturn();

        String responseBody = setupResult.getResponse().getContentAsString();
        String secret = objectMapper.readTree(responseBody).path("data").path("secret").asText();

        // 2. Confirm TOTP: POST /api/v1/users/me/mfa/totp/confirm
        long step = java.time.Instant.now().getEpochSecond() / 30;
        String totpCode = totpService.generateCodeForTimeStep(secret, step);

        MvcResult confirmResult = mockMvc.perform(post("/api/v1/users/me/mfa/totp/confirm")
                        .header("Authorization", "Bearer " + userAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpConfirmRequest(totpCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.recoveryCodes").isArray())
                .andReturn();

        List<String> recoveryCodes = objectMapper.readerForListOf(String.class)
                .readValue(objectMapper.readTree(confirmResult.getResponse().getContentAsString()).path("data").path("recoveryCodes"));
        assertThat(recoveryCodes).hasSize(10);

        // Verify user in DB now has mfa_enabled = true and encrypted secret
        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updated.isMfaEnabled()).isTrue();
        assertThat(updated.getTotpSecretEncrypted()).startsWith("v1:k1:");

        // 3. Step 1 Login with password now returns mfaRequired=true and intermediate mfaToken
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("mfa-user@example.com", "SecurePass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.mfaRequired", is(true)))
                .andExpect(jsonPath("$.data.mfaToken").exists())
                .andExpect(jsonPath("$.data.availableFactors").isArray())
                .andReturn();

        String mfaToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("mfaToken").asText();

        // 4. Verify that intermediate mfaToken CANNOT be used as access token on protected endpoints
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + mfaToken))
                .andExpect(status().isUnauthorized());

        // 5. Finish Step 2 Login with next TOTP step code: POST /api/v1/auth/mfa/verify
        long nextStep = step + 1;
        String nextTotpCode = totpService.generateCodeForTimeStep(secret, nextStep);

        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaVerifyRequest(mfaToken, "TOTP", nextTotpCode))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());

        // 6. Test Recovery code single use: Login again to step 1
        MvcResult secondLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("mfa-user@example.com", "SecurePass123!"))))
                .andExpect(status().isOk())
                .andReturn();
        String secondMfaToken = objectMapper.readTree(secondLogin.getResponse().getContentAsString()).path("data").path("mfaToken").asText();

        String recoveryCodeToUse = recoveryCodes.get(0);

        // Verify with recovery code succeeds
        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaVerifyRequest(secondMfaToken, "RECOVERY_CODE", recoveryCodeToUse))))
                .andExpect(status().isOk());

        // Reusing the same recovery code on a new login must fail!
        MvcResult thirdLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("mfa-user@example.com", "SecurePass123!"))))
                .andExpect(status().isOk())
                .andReturn();
        String thirdMfaToken = objectMapper.readTree(thirdLogin.getResponse().getContentAsString()).path("data").path("mfaToken").asText();

        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaVerifyRequest(thirdMfaToken, "RECOVERY_CODE", recoveryCodeToUse))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("E029")));
    }

    @Test
    @DisplayName("Step-up check rejects action if authentication token auth_time is older than step-up limit")
    void stepUpRejectsOldTokens() throws Exception {
        // Generate token with auth_time 20 minutes ago (limit is 10 mins in test profile)
        java.time.Instant twentyMinsAgo = java.time.Instant.now().minus(java.time.Duration.ofMinutes(20));
        String oldToken = jwtTokenService.generateAccessToken(testUser, List.of("pwd"), twentyMinsAgo);

        mockMvc.perform(post("/api/v1/users/me/change-password")
                        .header("Authorization", "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest("SecurePass123!", "NewSecurePass456!"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("E031")));
    }
}
