package com.iosocket.userservice.security;

import com.iosocket.userservice.config.AppProperties;
import com.iosocket.userservice.entity.Role;
import com.iosocket.userservice.entity.User;
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
                "user-service-test",
                "user-service-test-api",
                15,
                7,
                "",
                ""
        );
        AppProperties.AuthProperties authProps = new AppProperties.AuthProperties(5, 15);
        AppProperties.SecurityProperties secProps = new AppProperties.SecurityProperties(corsProps, jwtProps, authProps);
        AppProperties appProperties = new AppProperties(secProps);

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
        assertThat(signedJWT.getJWTClaimsSet().getIssuer()).isEqualTo("user-service-test");
        assertThat(signedJWT.getJWTClaimsSet().getAudience()).contains("user-service-test-api");
        assertThat(signedJWT.getJWTClaimsSet().getStringClaim("email")).isEqualTo("jane.doe@example.com");
        assertThat(signedJWT.getJWTClaimsSet().getStringListClaim("roles"))
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        assertThat(signedJWT.getJWTClaimsSet().getExpirationTime()).isNotNull();
    }
}
