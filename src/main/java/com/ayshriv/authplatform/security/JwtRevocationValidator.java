package com.ayshriv.authplatform.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class JwtRevocationValidator implements OAuth2TokenValidator<Jwt> {

    private final TokenDenylistService tokenDenylistService;

    public JwtRevocationValidator(TokenDenylistService tokenDenylistService) {
        this.tokenDenylistService = tokenDenylistService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String jti = token.getId();
        if (jti != null && tokenDenylistService.isRevoked(jti)) {
            OAuth2Error error = new OAuth2Error("invalid_token", "The token has been revoked", null);
            return OAuth2TokenValidatorResult.failure(error);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
