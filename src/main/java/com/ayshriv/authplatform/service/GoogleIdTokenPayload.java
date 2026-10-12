package com.ayshriv.authplatform.service;

public record GoogleIdTokenPayload(
        String sub,
        String email,
        boolean emailVerified,
        String givenName,
        String familyName,
        String picture,
        String hostedDomain
) {
}
