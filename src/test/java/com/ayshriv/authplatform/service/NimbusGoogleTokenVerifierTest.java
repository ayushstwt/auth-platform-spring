package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.exception.ForbiddenException;
import com.ayshriv.authplatform.exception.UnauthorizedException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NimbusGoogleTokenVerifierTest {

    private static final String ALLOWED_CLIENT_ID = "test-client-id.apps.googleusercontent.com";
    private static final String KEY_ID = "test-key-id-1";

    private RSAKey rsaJwk;
    private RSAPrivateKey privateKey;
    private AppProperties appProperties;
    private NimbusGoogleTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        privateKey = (RSAPrivateKey) kp.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) kp.getPublic();

        rsaJwk = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(KEY_ID)
                .build();

        JWKSet jwkSet = new JWKSet(rsaJwk.toPublicJWK());
        ImmutableJWKSet<?> jwkSource = new ImmutableJWKSet<>(jwkSet);

        AppProperties.CorsProperties cors = new AppProperties.CorsProperties(
                List.of("http://localhost"), List.of("GET"), List.of("*"), true, 3600
        );
        AppProperties.JwtProperties jwt = new AppProperties.JwtProperties(
                "issuer", "aud", 15, 7, "", ""
        );
        AppProperties.AuthProperties auth = new AppProperties.AuthProperties(5, 15, false, 10);
        AppProperties.SecurityProperties sec = new AppProperties.SecurityProperties(cors, jwt, auth);
        AppProperties.OtpProperties otp = new AppProperties.OtpProperties("pepper", 5, 5, 60, 5);
        AppProperties.EmailProperties email = new AppProperties.EmailProperties("no-reply@ayshriv.com", "Ayshriv");
        AppProperties.TwilioProperties twilio = new AppProperties.TwilioProperties("sid", "token", "+1234567890");
        AppProperties.SmsProperties sms = new AppProperties.SmsProperties("logging", "IN", List.of("IN", "US"), List.of("+1900"), 5, 20, 5, 100, 50, twilio);
        AppProperties.GoogleProperties google = new AppProperties.GoogleProperties(
                List.of(ALLOWED_CLIENT_ID), null, false, "http://localhost:3000", 60, "https://www.googleapis.com/oauth2/v3/certs"
        );

        appProperties = new AppProperties(sec, otp, email, sms, google);
        verifier = new NimbusGoogleTokenVerifier(appProperties, (com.nimbusds.jose.jwk.source.JWKSource) jwkSource);
    }

    private String createSignedToken(String sub, String email, Boolean emailVerified, String issuer, String audience, Instant exp, String hd) throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(sub)
                .issuer(issuer != null ? issuer : "https://accounts.google.com")
                .audience(audience != null ? audience : ALLOWED_CLIENT_ID)
                .issueTime(new Date())
                .expirationTime(Date.from(exp != null ? exp : Instant.now().plus(1, ChronoUnit.HOURS)))
                .jwtID(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("given_name", "Jane")
                .claim("family_name", "Doe");

        if (hd != null) {
            builder.claim("hd", hd);
        }

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(),
                builder.build()
        );
        signedJWT.sign(new RSASSASigner(privateKey));
        return signedJWT.serialize();
    }

    @Test
    @DisplayName("Should successfully verify valid Google ID token")
    void verify_Success() throws Exception {
        String token = createSignedToken("google-sub-123", "jane.doe@gmail.com", true, "https://accounts.google.com", ALLOWED_CLIENT_ID, null, null);

        GoogleIdTokenPayload payload = verifier.verify(token);

        assertThat(payload).isNotNull();
        assertThat(payload.sub()).isEqualTo("google-sub-123");
        assertThat(payload.email()).isEqualTo("jane.doe@gmail.com");
        assertThat(payload.emailVerified()).isTrue();
        assertThat(payload.givenName()).isEqualTo("Jane");
        assertThat(payload.familyName()).isEqualTo("Doe");
    }

    @Test
    @DisplayName("Should reject token when issuer is invalid")
    void verify_InvalidIssuer() throws Exception {
        String token = createSignedToken("google-sub-123", "jane.doe@gmail.com", true, "https://malicious-issuer.com", ALLOWED_CLIENT_ID, null, null);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid token issuer");
    }

    @Test
    @DisplayName("Should reject token when audience does not match allowed client IDs")
    void verify_InvalidAudience() throws Exception {
        String token = createSignedToken("google-sub-123", "jane.doe@gmail.com", true, "https://accounts.google.com", "foreign-client-id.apps.googleusercontent.com", null, null);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Token audience mismatch");
    }

    @Test
    @DisplayName("Should reject expired Google token")
    void verify_ExpiredToken() throws Exception {
        Instant pastExp = Instant.now().minus(10, ChronoUnit.MINUTES);
        String token = createSignedToken("google-sub-123", "jane.doe@gmail.com", true, "https://accounts.google.com", ALLOWED_CLIENT_ID, pastExp, null);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Google ID token has expired");
    }

    @Test
    @DisplayName("Should reject token when Google email is unverified")
    void verify_UnverifiedEmail() throws Exception {
        String token = createSignedToken("google-sub-123", "jane.doe@gmail.com", false, "https://accounts.google.com", ALLOWED_CLIENT_ID, null, null);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Google account email is not verified");
    }

    @Test
    @DisplayName("Should reject forged signature signed by an untrusted key")
    void verify_ForgedSignature() throws Exception {
        KeyPairGenerator otherKpg = KeyPairGenerator.getInstance("RSA");
        otherKpg.initialize(2048);
        KeyPair otherKp = otherKpg.generateKeyPair();

        SignedJWT forgedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("untrusted-key").build(),
                new JWTClaimsSet.Builder()
                        .subject("sub")
                        .issuer("https://accounts.google.com")
                        .audience(ALLOWED_CLIENT_ID)
                        .claim("email", "hacker@example.com")
                        .claim("email_verified", true)
                        .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                        .build()
        );
        forgedJWT.sign(new RSASSASigner(otherKp.getPrivate()));
        String forgedToken = forgedJWT.serialize();

        assertThatThrownBy(() -> verifier.verify(forgedToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Google token signature verification failed");
    }

    @Test
    @DisplayName("Should reject token when hosted domain (hd) restriction does not match")
    void verify_HostedDomainMismatch() throws Exception {
        AppProperties.GoogleProperties googleWithHd = new AppProperties.GoogleProperties(
                List.of(ALLOWED_CLIENT_ID), "company.com", false, "http://localhost:3000", 60, "https://www.googleapis.com/oauth2/v3/certs"
        );
        AppProperties propsWithHd = new AppProperties(
                appProperties.security(), appProperties.otp(), appProperties.email(), appProperties.sms(), googleWithHd
        );
        JWKSet jwkSet = new JWKSet(rsaJwk.toPublicJWK());
        NimbusGoogleTokenVerifier hdVerifier = new NimbusGoogleTokenVerifier(propsWithHd, (com.nimbusds.jose.jwk.source.JWKSource) new ImmutableJWKSet<>(jwkSet));

        String tokenWithoutHd = createSignedToken("sub-1", "user@gmail.com", true, "https://accounts.google.com", ALLOWED_CLIENT_ID, null, null);

        assertThatThrownBy(() -> hdVerifier.verify(tokenWithoutHd))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Google account domain is not authorized");
    }
}
