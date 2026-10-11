package com.ayshriv.authplatform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Unified authentication response representing either successful completion with issued tokens,
 * or an intermediate MFA challenge requirement.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResult(
        boolean mfaRequired,
        String mfaToken,
        List<String> availableFactors,
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        UserResponse user
) {
    public static LoginResult tokens(AuthResponse authResponse) {
        return new LoginResult(
                false,
                null,
                null,
                authResponse.accessToken(),
                authResponse.refreshToken(),
                authResponse.tokenType(),
                authResponse.expiresIn(),
                authResponse.user()
        );
    }

    public static LoginResult mfaChallenge(String mfaToken, List<String> availableFactors) {
        return new LoginResult(
                true,
                mfaToken,
                availableFactors,
                null,
                null,
                null,
                null,
                null
        );
    }
}
