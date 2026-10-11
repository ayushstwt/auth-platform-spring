package com.ayshriv.authplatform.security;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.service.GoogleAuthService;
import com.ayshriv.authplatform.service.GoogleIdTokenPayload;
import com.ayshriv.authplatform.service.OAuthExchangeCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private GoogleAuthService googleAuthService;
    @Mock
    private OAuthExchangeCodeService oAuthExchangeCodeService;
    @Mock
    private Authentication authentication;

    private OAuth2LoginSuccessHandler successHandler;

    @BeforeEach
    void setUp() {
        AppProperties.CorsProperties cors = new AppProperties.CorsProperties(List.of("*"), List.of("GET"), List.of("*"), false, 3600);
        AppProperties.JwtProperties jwt = new AppProperties.JwtProperties("iss", "aud", 15, 7, "", "");
        AppProperties.AuthProperties auth = new AppProperties.AuthProperties(5, 15, false, 10);
        AppProperties.SecurityProperties sec = new AppProperties.SecurityProperties(cors, jwt, auth);
        AppProperties.OtpProperties otp = new AppProperties.OtpProperties("pepper", 5, 5, 60, 5);
        AppProperties.EmailProperties email = new AppProperties.EmailProperties("test@ayshriv.com", "Auth");
        AppProperties.TwilioProperties twilio = new AppProperties.TwilioProperties("sid", "tok", "+1");
        AppProperties.SmsProperties sms = new AppProperties.SmsProperties("logging", "IN", List.of("IN"), List.of(), 5, 20, 5, 100, 50, twilio);
        AppProperties.GoogleProperties google = new AppProperties.GoogleProperties(
                List.of("client-1"), null, true, "http://localhost:3000/oauth2/callback", 60, "https://www.googleapis.com/oauth2/v3/certs"
        );

        AppProperties appProperties = new AppProperties(sec, otp, email, sms, google);
        successHandler = new OAuth2LoginSuccessHandler(googleAuthService, oAuthExchangeCodeService, appProperties);
    }

    @Test
    @DisplayName("Should process OidcUser, generate exchange code, and redirect without exposing tokens")
    void onAuthenticationSuccess_RedirectsWithCode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        OidcIdToken idToken = new OidcIdToken("tokenValue", Instant.now(), Instant.now().plusSeconds(3600), Map.of(
                "sub", "google-sub-777",
                "email", "alice@example.com",
                "email_verified", true,
                "given_name", "Alice",
                "family_name", "Smith"
        ));
        OidcUser oidcUser = new DefaultOidcUser(List.of(), idToken);

        when(authentication.getPrincipal()).thenReturn(oidcUser);

        User user = new User(77L);
        when(googleAuthService.processGooglePayload(any(GoogleIdTokenPayload.class))).thenReturn(user);
        when(oAuthExchangeCodeService.generateCode(77L)).thenReturn("one-time-exchange-code-123");

        successHandler.onAuthenticationSuccess(request, response, authentication);

        String redirectedUrl = response.getRedirectedUrl();
        assertThat(redirectedUrl).isNotNull();
        assertThat(redirectedUrl).startsWith("http://localhost:3000/oauth2/callback?code=one-time-exchange-code-123");
        assertThat(redirectedUrl).doesNotContain("access_token");
        assertThat(redirectedUrl).doesNotContain("refresh_token");
    }
}
