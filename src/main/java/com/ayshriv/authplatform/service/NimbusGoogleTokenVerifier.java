package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.exception.ForbiddenException;
import com.ayshriv.authplatform.exception.UnauthorizedException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
public class NimbusGoogleTokenVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(NimbusGoogleTokenVerifier.class);
    private static final Set<String> ALLOWED_ISSUERS = Set.of(
            "https://accounts.google.com",
            "accounts.google.com"
    );

    private final AppProperties appProperties;
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor;

    @org.springframework.beans.factory.annotation.Autowired
    public NimbusGoogleTokenVerifier(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.jwtProcessor = createDefaultJwtProcessor(appProperties.google().jwksUri());
    }

    /**
     * Constructor for injecting custom JWKSource (e.g. for unit and integration testing without network calls).
     */
    public NimbusGoogleTokenVerifier(AppProperties appProperties, JWKSource<SecurityContext> jwkSource) {
        this.appProperties = appProperties;
        this.jwtProcessor = new DefaultJWTProcessor<>();
        JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);
        this.jwtProcessor.setJWSKeySelector(keySelector);
    }

    private ConfigurableJWTProcessor<SecurityContext> createDefaultJwtProcessor(String jwksUri) {
        try {
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            JWKSource<SecurityContext> jwkSource = new RemoteJWKSet<>(new URL(jwksUri));
            JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);
            processor.setJWSKeySelector(keySelector);
            return processor;
        } catch (Exception e) {
            log.error("Failed to initialize Google JWKS key selector with URI: {}", jwksUri, e);
            throw new IllegalStateException("Failed to initialize Google JWKS key selector", e);
        }
    }

    @Override
    public GoogleIdTokenPayload verify(String idTokenString) {
        if (idTokenString == null || idTokenString.trim().isEmpty()) {
            throw new UnauthorizedException(ErrorMessages.E022, "Google ID token must not be empty");
        }

        JWTClaimsSet claims;
        try {
            claims = jwtProcessor.process(idTokenString.trim(), null);
        } catch (Exception e) {
            // Check if failure was specifically due to expiration
            try {
                com.nimbusds.jwt.SignedJWT unverifiedJwt = com.nimbusds.jwt.SignedJWT.parse(idTokenString.trim());
                Date exp = unverifiedJwt.getJWTClaimsSet().getExpirationTime();
                if (exp != null && exp.before(new Date())) {
                    log.warn("Google ID token is expired: exp={}", exp);
                    throw new UnauthorizedException(ErrorMessages.E022, "Google ID token has expired");
                }
            } catch (UnauthorizedException ue) {
                throw ue;
            } catch (Exception ignored) {
                // Ignore parse errors from fallback
            }

            log.warn("Google ID token signature verification failed: {}", e.getMessage());
            throw new UnauthorizedException(ErrorMessages.E022, "Google token signature verification failed");
        }

        // 1. Validate Expiration
        Date exp = claims.getExpirationTime();
        if (exp == null || exp.before(new Date())) {
            log.warn("Google ID token is expired: exp={}", exp);
            throw new UnauthorizedException(ErrorMessages.E022, "Google ID token has expired");
        }

        // 2. Validate Issuer
        String issuer = claims.getIssuer();
        if (issuer == null || !ALLOWED_ISSUERS.contains(issuer)) {
            log.warn("Invalid issuer in Google token: {}", issuer);
            throw new UnauthorizedException(ErrorMessages.E022, "Invalid token issuer");
        }

        // 3. Validate Audience
        List<String> audience = claims.getAudience();
        List<String> allowedClientIds = appProperties.google().clientIds();
        if (audience == null || audience.stream().noneMatch(allowedClientIds::contains)) {
            log.warn("Audience mismatch in Google token. Token aud: {}, allowed: {}", audience, allowedClientIds);
            throw new UnauthorizedException(ErrorMessages.E022, "Token audience mismatch");
        }

        // 4. Validate Subject
        String sub = claims.getSubject();
        if (sub == null || sub.trim().isEmpty()) {
            log.warn("Subject missing in Google token");
            throw new UnauthorizedException(ErrorMessages.E022, "Subject is missing in Google token");
        }

        String email;
        Boolean emailVerified;
        String hostedDomainClaim;
        String givenName;
        String familyName;
        String picture;

        try {
            email = claims.getStringClaim("email");
            emailVerified = claims.getBooleanClaim("email_verified");
            hostedDomainClaim = claims.getStringClaim("hd");
            givenName = claims.getStringClaim("given_name");
            familyName = claims.getStringClaim("family_name");
            picture = claims.getStringClaim("picture");
        } catch (java.text.ParseException e) {
            log.warn("Malformed claims in Google ID token: {}", e.getMessage());
            throw new UnauthorizedException(ErrorMessages.E022, "Malformed claims in token");
        }

        // 5. Validate Email and Email Verified
        if (email == null || email.trim().isEmpty()) {
            log.warn("Email missing in Google token");
            throw new UnauthorizedException(ErrorMessages.E022, "Email missing in Google token");
        }
        if (emailVerified == null || !emailVerified) {
            log.warn("Email in Google token is not verified");
            throw new UnauthorizedException(ErrorMessages.E022, "Google account email is not verified");
        }

        // 6. Validate Hosted Domain (hd) restriction if configured
        String requiredDomain = appProperties.google().hostedDomain();
        if (requiredDomain != null && !requiredDomain.trim().isEmpty()) {
            if (hostedDomainClaim == null || !requiredDomain.equalsIgnoreCase(hostedDomainClaim)) {
                log.warn("Hosted domain mismatch. Token hd: {}, required hd: {}", hostedDomainClaim, requiredDomain);
                throw new ForbiddenException(ErrorMessages.E026, "Google account domain is not authorized");
            }
        }

        return new GoogleIdTokenPayload(
                sub,
                email.trim().toLowerCase(),
                emailVerified,
                givenName,
                familyName,
                picture,
                hostedDomainClaim
        );
    }
}
