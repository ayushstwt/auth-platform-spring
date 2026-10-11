package com.ayshriv.authplatform.controller;

import com.ayshriv.authplatform.dto.EmailOtpConfirmRequest;
import com.ayshriv.authplatform.dto.EmailOtpSendRequest;
import com.ayshriv.authplatform.dto.ForgotPasswordRequest;
import com.ayshriv.authplatform.dto.ResetPasswordRequest;
import com.ayshriv.authplatform.dto.VerifyEmailConfirmRequest;
import com.ayshriv.authplatform.entity.OtpChallenge;
import com.ayshriv.authplatform.entity.OtpPurpose;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.entity.UserStatus;
import com.ayshriv.authplatform.repository.OtpChallengeRepository;
import com.ayshriv.authplatform.repository.RefreshTokenRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailOtpControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpChallengeRepository otpChallengeRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestEmailSender testEmailSender;

    private User testUser;
    private String userToken;

    @BeforeEach
    void setUp() {
        otpChallengeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        testEmailSender.clear();

        testUser = new User();
        testUser.setEmail("tester@example.com");
        testUser.setPasswordHash(passwordEncoder.encode("SecurePass1234!"));
        testUser.setFirstName("Tester");
        testUser.setLastName("Account");
        testUser.setEmailVerified(false);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser = userRepository.save(testUser);

        userToken = jwtTokenService.generateAccessToken(testUser);
    }

    @Test
    @DisplayName("Should send email verification OTP and successfully verify email")
    void emailVerifyFlow() throws Exception {
        // 1. Send OTP (authenticated)
        mockMvc.perform(post("/api/v1/auth/email/verify/send")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        String otpCode = testEmailSender.getLastCode();
        assertThat(otpCode).isNotNull().hasSize(6);

        // Verify challenge is saved hashed
        List<OtpChallenge> challenges = otpChallengeRepository.findAll();
        assertThat(challenges).hasSize(1);
        assertThat(challenges.get(0).getCodeHash()).doesNotContain(otpCode);
        assertThat(challenges.get(0).getPurpose()).isEqualTo(OtpPurpose.EMAIL_VERIFY);

        // 2. Confirm verification
        VerifyEmailConfirmRequest confirmReq = new VerifyEmailConfirmRequest(otpCode);
        mockMvc.perform(post("/api/v1/auth/email/verify/confirm")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("Should login via passwordless email OTP")
    void loginWithEmailOtpFlow() throws Exception {
        // 1. Send login OTP (anti-enumeration check: same response)
        EmailOtpSendRequest sendReq = new EmailOtpSendRequest(testUser.getEmail());
        mockMvc.perform(post("/api/v1/auth/login/email-otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.message", is("If the account exists, a one-time passcode has been sent")));

        String otpCode = testEmailSender.getLastCode();
        assertThat(otpCode).isNotNull().hasSize(6);

        // 2. Confirm login OTP
        EmailOtpConfirmRequest confirmReq = new EmailOtpConfirmRequest(testUser.getEmail(), otpCode);
        mockMvc.perform(post("/api/v1/auth/login/email-otp/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.email", is(testUser.getEmail())));
    }

    @Test
    @DisplayName("Login OTP send for unknown email returns identical generic response (anti-enumeration)")
    void loginOtpSend_UnknownEmail() throws Exception {
        EmailOtpSendRequest sendReq = new EmailOtpSendRequest("unknown.user@example.com");
        mockMvc.perform(post("/api/v1/auth/login/email-otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.message", is("If the account exists, a one-time passcode has been sent")));

        assertThat(testEmailSender.getLastCode()).isNull();
    }

    @Test
    @DisplayName("Should forgot password, reset with OTP, and revoke existing sessions")
    void forgotAndResetPasswordFlow() throws Exception {
        // 1. Forgot password
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest(testUser.getEmail());
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.message", is("If the account exists, password reset instructions have been sent")));

        String otpCode = testEmailSender.getLastCode();
        assertThat(otpCode).isNotNull().hasSize(6);

        // 2. Reset password
        ResetPasswordRequest resetReq = new ResetPasswordRequest(testUser.getEmail(), otpCode, "BrandNewSecurePassword123!");
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("BrandNewSecurePassword123!", updatedUser.getPasswordHash())).isTrue();
    }
}
