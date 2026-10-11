package com.ayshriv.authplatform.controller;

import com.ayshriv.authplatform.dto.GoogleTokenLoginRequest;
import com.ayshriv.authplatform.entity.FederatedIdentity;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.repository.FederatedIdentityRepository;
import com.ayshriv.authplatform.repository.UserRepository;
import com.ayshriv.authplatform.security.JwtTokenService;
import com.ayshriv.authplatform.service.GoogleAuthService;
import com.ayshriv.authplatform.service.OAuthExchangeCodeService;
import com.ayshriv.authplatform.service.TestEmailSender;
import com.ayshriv.authplatform.service.TestGoogleTokenVerifierConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GoogleAuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FederatedIdentityRepository federatedIdentityRepository;

    @Autowired
    private TestGoogleTokenVerifierConfig testGoogleConfig;

    @Autowired
    private TestEmailSender testEmailSender;

    @Autowired
    private OAuthExchangeCodeService oAuthExchangeCodeService;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private RSAKey testRsaKey;

    @BeforeEach
    void setUp() {
        testRsaKey = testGoogleConfig.getTestRsaKey();
        testEmailSender.clear();
    }

    private String createValidToken(String sub, String email, boolean emailVerified) throws Exception {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(sub)
                .issuer("https://accounts.google.com")
                .audience("test-google-client-id-1")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .jwtID(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("given_name", "Test")
                .claim("family_name", "User")
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(testRsaKey.getKeyID()).build(),
                claimsSet
        );
        signedJWT.sign(new RSASSASigner(testRsaKey.toRSAPrivateKey()));
        return signedJWT.serialize();
    }

    @Test
    @DisplayName("Should sign up and log in new user via Google ID token (Scenario 4)")
    void googleLogin_NewUser_Success() throws Exception {
        String token = createValidToken("google-sub-999", "new.google.user@example.com", true);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleTokenLoginRequest(token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("new.google.user@example.com"))
                .andExpect(jsonPath("$.data.user.emailVerified").value(true))
                .andExpect(jsonPath("$.data.user.hasPassword").value(false));

        // Verify entity persisted in DB
        User user = userRepository.findByEmail("new.google.user@example.com").orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.isHasPassword()).isFalse();

        FederatedIdentity identity = federatedIdentityRepository
                .findByProviderAndProviderSubject(GoogleAuthService.PROVIDER_GOOGLE, "google-sub-999")
                .orElseThrow();
        assertThat(identity.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("Should auto-link to existing verified account and send alert email (Scenario 2)")
    void googleLogin_AutoLinkVerifiedAccount_Success() throws Exception {
        User existingUser = new User();
        existingUser.setEmail("existing.verified@example.com");
        existingUser.setPasswordHash(passwordEncoder.encode("SecurePass123!"));
        existingUser.setFirstName("Existing");
        existingUser.setLastName("User");
        existingUser.setEmailVerified(true);
        existingUser.setHasPassword(true);
        userRepository.save(existingUser);

        String token = createValidToken("google-sub-888", "existing.verified@example.com", true);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleTokenLoginRequest(token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.user.email").value("existing.verified@example.com"))
                .andExpect(jsonPath("$.data.user.hasPassword").value(true));

        // Verify identity linked
        assertThat(federatedIdentityRepository.existsByProviderAndProviderSubject(GoogleAuthService.PROVIDER_GOOGLE, "google-sub-888"))
                .isTrue();

        // Verify alert email was sent
        assertThat(testEmailSender.getLastRecipient()).isEqualTo("existing.verified@example.com");
        assertThat(testEmailSender.getLastType()).isEqualTo("ACCOUNT_LINKED");
    }

    @Test
    @DisplayName("Should reject Google login when existing local account has unverified email (Scenario 3)")
    void googleLogin_BlockUnverifiedAccount() throws Exception {
        User unverifiedUser = new User();
        unverifiedUser.setEmail("existing.unverified@example.com");
        unverifiedUser.setPasswordHash(passwordEncoder.encode("SecurePass123!"));
        unverifiedUser.setFirstName("Unverified");
        unverifiedUser.setLastName("User");
        unverifiedUser.setEmailVerified(false);
        userRepository.save(unverifiedUser);

        String token = createValidToken("google-sub-777", "existing.unverified@example.com", true);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleTokenLoginRequest(token))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("E023"));
    }

    @Test
    @DisplayName("Should login existing federated user directly (Scenario 1)")
    void googleLogin_ExistingFederatedUser_Success() throws Exception {
        User user = new User();
        user.setEmail("fed.user@example.com");
        user.setPasswordHash(passwordEncoder.encode("Pass123!"));
        user.setFirstName("Fed");
        user.setLastName("User");
        user.setEmailVerified(true);
        User savedUser = userRepository.save(user);

        FederatedIdentity identity = new FederatedIdentity(savedUser, GoogleAuthService.PROVIDER_GOOGLE, "google-sub-666", savedUser.getEmail());
        federatedIdentityRepository.save(identity);

        String token = createValidToken("google-sub-666", "fed.user@example.com", true);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleTokenLoginRequest(token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.user.email").value("fed.user@example.com"));

        // No new email should be sent for regular login
        assertThat(testEmailSender.getLastRecipient()).isNull();
    }

    @Test
    @DisplayName("Should reject forged Google token signed with invalid key")
    void googleLogin_ForgedToken_Fails() throws Exception {
        String forgedToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NSJ9.invalid-sig";

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleTokenLoginRequest(forgedToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("E022"));
    }

    @Test
    @DisplayName("Should exchange one-time code for JWT tokens")
    void googleExchange_Success() throws Exception {
        User user = new User();
        user.setEmail("exchange.user@example.com");
        user.setPasswordHash("hash");
        user.setFirstName("Exchange");
        user.setLastName("User");
        user.setEmailVerified(true);
        User saved = userRepository.save(user);

        String code = oAuthExchangeCodeService.generateCode(saved.getId());

        mockMvc.perform(post("/api/v1/auth/google/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchangeCode\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // Second exchange attempt must fail
        mockMvc.perform(post("/api/v1/auth/google/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchangeCode\":\"" + code + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("E027"));
    }

    @Test
    @DisplayName("Authenticated user can link and unlink Google account (with password protection)")
    void linkAndUnlinkGoogleAccount() throws Exception {
        User user = new User();
        user.setEmail("linktest@example.com");
        user.setPasswordHash(passwordEncoder.encode("Pass1234!"));
        user.setFirstName("Link");
        user.setLastName("Test");
        user.setEmailVerified(true);
        user.setHasPassword(true);
        User saved = userRepository.save(user);

        String accessToken = jwtTokenService.generateAccessToken(saved);
        String googleToken = createValidToken("google-sub-555", "linktest@example.com", true);

        // 1. Link Google
        mockMvc.perform(post("/api/v1/users/me/identities/google/link")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleTokenLoginRequest(googleToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.provider").value("GOOGLE"));

        // 2. Get identities
        mockMvc.perform(get("/api/v1/users/me/identities")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].provider").value("GOOGLE"));

        // 3. Unlink Google (Allowed because user has password)
        mockMvc.perform(delete("/api/v1/users/me/identities/google")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // 4. Verify unlinked
        assertThat(federatedIdentityRepository.existsByUserIdAndProvider(saved.getId(), GoogleAuthService.PROVIDER_GOOGLE))
                .isFalse();
    }

    @Test
    @DisplayName("Should block unlinking Google when user has no password and no other login method")
    void unlinkGoogle_BlockedWhenNoPassword() throws Exception {
        User googleOnlyUser = new User();
        googleOnlyUser.setEmail("no.password@example.com");
        googleOnlyUser.setPasswordHash("unusable-hash");
        googleOnlyUser.setFirstName("Google");
        googleOnlyUser.setLastName("Only");
        googleOnlyUser.setEmailVerified(true);
        googleOnlyUser.setHasPassword(false);
        User saved = userRepository.save(googleOnlyUser);

        FederatedIdentity identity = new FederatedIdentity(saved, GoogleAuthService.PROVIDER_GOOGLE, "sub-nopass", saved.getEmail());
        federatedIdentityRepository.save(identity);

        String accessToken = jwtTokenService.generateAccessToken(saved);

        mockMvc.perform(delete("/api/v1/users/me/identities/google")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("E024"));
    }
}
