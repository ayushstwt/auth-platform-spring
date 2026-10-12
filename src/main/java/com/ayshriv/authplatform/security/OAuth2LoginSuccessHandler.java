package com.ayshriv.authplatform.security;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.service.GoogleAuthService;
import com.ayshriv.authplatform.service.GoogleIdTokenPayload;
import com.ayshriv.authplatform.service.OAuthExchangeCodeService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

    private final GoogleAuthService googleAuthService;
    private final OAuthExchangeCodeService oAuthExchangeCodeService;
    private final AppProperties appProperties;

    public OAuth2LoginSuccessHandler(GoogleAuthService googleAuthService,
                                     OAuthExchangeCodeService oAuthExchangeCodeService,
                                     AppProperties appProperties) {
        this.googleAuthService = googleAuthService;
        this.oAuthExchangeCodeService = oAuthExchangeCodeService;
        this.appProperties = appProperties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        Object principal = authentication.getPrincipal();
        String sub = null;
        String email = null;
        boolean emailVerified = false;
        String givenName = null;
        String familyName = null;
        String picture = null;
        String hd = null;

        if (principal instanceof OidcUser oidcUser) {
            sub = oidcUser.getSubject();
            email = oidcUser.getEmail();
            Boolean ev = oidcUser.getEmailVerified();
            emailVerified = ev != null && ev;
            givenName = oidcUser.getGivenName();
            familyName = oidcUser.getFamilyName();
            picture = oidcUser.getPicture();
            hd = oidcUser.getClaimAsString("hd");
        } else if (principal instanceof OAuth2User oauth2User) {
            sub = oauth2User.getName();
            email = oauth2User.getAttribute("email");
            Boolean ev = oauth2User.getAttribute("email_verified");
            emailVerified = ev != null && ev;
            givenName = oauth2User.getAttribute("given_name");
            familyName = oauth2User.getAttribute("family_name");
            picture = oauth2User.getAttribute("picture");
            hd = oauth2User.getAttribute("hd");
        }

        if (sub == null || email == null) {
            log.error("OAuth2 authentication succeeded but claims sub or email are missing");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing required claims from OAuth provider");
            return;
        }

        GoogleIdTokenPayload payload = new GoogleIdTokenPayload(
                sub,
                email.trim().toLowerCase(),
                emailVerified,
                givenName,
                familyName,
                picture,
                hd
        );

        var user = googleAuthService.processGooglePayload(payload);
        String exchangeCode = oAuthExchangeCodeService.generateCode(user.getId());

        String targetUrl = UriComponentsBuilder.fromUriString(appProperties.google().redirectSuccessUrl())
                .queryParam("code", exchangeCode)
                .build()
                .toUriString();

        log.info("OAuth2 login completed for user id={}; redirecting with one-time exchange code", user.getId());
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
