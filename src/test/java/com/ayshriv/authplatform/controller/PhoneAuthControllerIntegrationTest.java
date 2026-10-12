package com.ayshriv.authplatform.controller;

import com.ayshriv.authplatform.dto.PhoneLoginConfirmRequest;
import com.ayshriv.authplatform.dto.PhoneLoginSendRequest;
import com.ayshriv.authplatform.dto.PhoneOtpSendRequest;
import com.ayshriv.authplatform.dto.PhoneVerifyRequest;
import com.ayshriv.authplatform.entity.OtpChallenge;
import com.ayshriv.authplatform.entity.OtpChannel;
import com.ayshriv.authplatform.entity.OtpPurpose;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.entity.UserStatus;
import com.ayshriv.authplatform.repository.OtpChallengeRepository;
import com.ayshriv.authplatform.repository.RefreshTokenRepository;
import com.ayshriv.authplatform.repository.UserRepository;
import com.ayshriv.authplatform.security.JwtTokenService;
import com.ayshriv.authplatform.service.SmsAbuseProtectionService;
import com.ayshriv.authplatform.service.TestSmsSender;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class PhoneAuthControllerIntegrationTest {

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
    private TestSmsSender testSmsSender;

    @Autowired
    private SmsAbuseProtectionService smsAbuseProtectionService;

    private User testUser;
    private String userToken;

    @BeforeEach
    void setUp() {
        otpChallengeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        testSmsSender.clear();
        smsAbuseProtectionService.resetDailyCounter();

        testUser = new User();
        testUser.setEmail("phoneuser@example.com");
        testUser.setPasswordHash(passwordEncoder.encode("SecurePass1234!"));
        testUser.setFirstName("Phone");
        testUser.setLastName("User");
        testUser.setEmailVerified(true);
        testUser.setStatus(UserStatus.ACTIVE);
        testUser.setLastLoginAt(Instant.now());
        testUser = userRepository.save(testUser);

        userToken = jwtTokenService.generateAccessToken(testUser);
    }

    @Test
    @DisplayName("Should send phone OTP, verify phone, and set phone_verified=true")
    void sendAndVerifyPhoneFlow() throws Exception {
        // 1. Send OTP to add phone
        PhoneOtpSendRequest sendReq = new PhoneOtpSendRequest("9876543210");
        mockMvc.perform(post("/api/v1/users/me/phone/send-otp")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        String code = testSmsSender.getLastCode();
        assertThat(code).isNotNull().hasSize(6);
        assertThat(testSmsSender.getLastRecipient()).isEqualTo("+919876543210");

        // Verify challenge is stored hashed in DB
        List<OtpChallenge> challenges = otpChallengeRepository.findAll();
        assertThat(challenges).hasSize(1);
        assertThat(challenges.get(0).getChannel()).isEqualTo(OtpChannel.SMS);
        assertThat(challenges.get(0).getPurpose()).isEqualTo(OtpPurpose.PHONE_VERIFY);
        assertThat(challenges.get(0).getCodeHash()).doesNotContain(code);

        // 2. Verify code
        PhoneVerifyRequest verifyReq = new PhoneVerifyRequest("9876543210", code);
        mockMvc.perform(post("/api/v1/users/me/phone/verify")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.phoneE164", is("+919876543210")))
                .andExpect(jsonPath("$.data.phoneVerified", is(true)));

        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updated.getPhoneE164()).isEqualTo("+919876543210");
        assertThat(updated.isPhoneVerified()).isTrue();
    }

    @Test
    @DisplayName("Changing verified phone requires recent re-authentication")
    void changeVerifiedPhoneRequiresRecentLogin() throws Exception {
        // Mark user with verified phone and old last login (> 10 mins ago)
        testUser.setPhoneE164("+919876543210");
        testUser.setPhoneVerified(true);
        testUser.setLastLoginAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        userRepository.save(testUser);

        PhoneOtpSendRequest sendReq = new PhoneOtpSendRequest("9876543211");
        mockMvc.perform(post("/api/v1/users/me/phone/send-otp")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode", is("E020")));
    }

    @Test
    @DisplayName("Cannot claim a phone number already registered to another user")
    void rejectDuplicatePhoneNumber() throws Exception {
        User anotherUser = new User();
        anotherUser.setEmail("another@example.com");
        anotherUser.setPasswordHash(passwordEncoder.encode("Pass123!"));
        anotherUser.setFirstName("Other");
        anotherUser.setLastName("User");
        anotherUser.setPhoneE164("+919876543210");
        anotherUser.setPhoneVerified(true);
        userRepository.save(anotherUser);

        PhoneOtpSendRequest sendReq = new PhoneOtpSendRequest("9876543210");
        mockMvc.perform(post("/api/v1/users/me/phone/send-otp")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("E003")));
    }

    @Test
    @DisplayName("Should successfully login via phone SMS OTP if phone is verified")
    void loginWithPhoneOtpFlow() throws Exception {
        // Prepare verified phone for testUser
        testUser.setPhoneE164("+919876543210");
        testUser.setPhoneVerified(true);
        userRepository.save(testUser);

        // 1. Send login OTP
        PhoneLoginSendRequest sendReq = new PhoneLoginSendRequest("9876543210");
        mockMvc.perform(post("/api/v1/auth/login/phone-otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        String code = testSmsSender.getLastCode();
        assertThat(code).isNotNull().hasSize(6);

        // 2. Confirm login OTP
        PhoneLoginConfirmRequest confirmReq = new PhoneLoginConfirmRequest("9876543210", code);
        mockMvc.perform(post("/api/v1/auth/login/phone-otp/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.email", is(testUser.getEmail())))
                .andExpect(jsonPath("$.data.user.phoneE164", is("+919876543210")));
    }

    @Test
    @DisplayName("Unverified phone cannot be used for SMS login")
    void unverifiedPhoneCannotLogin() throws Exception {
        testUser.setPhoneE164("+919876543210");
        testUser.setPhoneVerified(false); // Unverified!
        userRepository.save(testUser);

        PhoneLoginSendRequest sendReq = new PhoneLoginSendRequest("9876543210");
        mockMvc.perform(post("/api/v1/auth/login/phone-otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));

        // No SMS code should be sent to unverified phone
        assertThat(testSmsSender.getLastCode()).isNull();
    }
}
