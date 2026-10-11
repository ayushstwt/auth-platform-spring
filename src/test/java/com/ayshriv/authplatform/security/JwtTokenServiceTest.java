package com.ayshriv.authplatform.security;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.entity.Role;
import com.ayshriv.authplatform.entity.User;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        AppProperties.CorsProperties corsProps = new AppProperties.CorsProperties(
                List.of("http://localhost:3000"),
                List.of("GET", "POST"),
                List.of("Authorization", "Content-Type"),
                true,
                3600
        );
        AppProperties.JwtProperties jwtProps = new AppProperties.JwtProperties(
                "auth-platform-spring-test",
                "auth-platform-spring-test-api",
                15,
                7,
                "",
                ""
        );
        AppProperties.AuthProperties authProps = new AppProperties.AuthProperties(5, 15, false, 10);
        AppProperties.SecurityProperties secProps = new AppProperties.SecurityProperties(corsProps, jwtProps, authProps);
        AppProperties.OtpProperties otpProps = new AppProperties.OtpProperties("test-pepper", 5, 5, 60, 5);
        AppProperties.EmailProperties emailProps = new AppProperties.EmailProperties("test@ayshriv.com", "Test Auth");
        AppProperties.TwilioProperties twilioProps = new AppProperties.TwilioProperties("sid", "token", "+1234567890");
        AppProperties.SmsProperties smsProps = new AppProperties.SmsProperties("logging", "IN", List.of("IN", "US"), List.of("+1900"), 5, 20, 5, 100, 50, twilioProps);
        AppProperties.GoogleProperties googleProps = new AppProperties.GoogleProperties(List.of("client-1"), null, false, "http://localhost:3000", 60, "https://www.googleapis.com/oauth2/v3/certs");
        AppProperties appProperties = new AppProperties(secProps, otpProps, emailProps, smsProps, googleProps);

        RsaKeyProvider rsaKeyProvider = new RsaKeyProvider(appProperties);
        rsaKeyProvider.init();

        jwtTokenService = new JwtTokenService(rsaKeyProvider, appProperties);
    }

    @Test
    @DisplayName("Should generate valid signed RS256 token with standard claims")
    void generateAccessToken() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setEmail("jane.doe@example.com");
        user.setRoles(Set.of(new Role(Role.ROLE_USER), new Role(Role.ROLE_ADMIN)));

        String token = jwtTokenService.generateAccessToken(user);

        assertThat(token).isNotBlank();

        SignedJWT signedJWT = SignedJWT.parse(token);
        assertThat(signedJWT.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(signedJWT.getHeader().getKeyID()).isNotBlank();
        assertThat(signedJWT.getJWTClaimsSet().getSubject()).isEqualTo("42");
        assertThat(signedJWT.getJWTClaimsSet().getIssuer()).isEqualTo("auth-platform-spring-test");
        assertThat(signedJWT.getJWTClaimsSet().getAudience()).contains("auth-platform-spring-test-api");
        assertThat(signedJWT.getJWTClaimsSet().getStringClaim("email")).isEqualTo("jane.doe@example.com");
        assertThat(signedJWT.getJWTClaimsSet().getStringListClaim("roles"))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        assertThat(signedJWT.getJWTClaimsSet().getExpirationTime()).isNotNull();
    }
}
