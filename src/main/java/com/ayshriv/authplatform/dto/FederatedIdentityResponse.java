package com.ayshriv.authplatform.dto;

import com.ayshriv.authplatform.entity.FederatedIdentity;

import java.time.Instant;

public record FederatedIdentityResponse(
        Long id,
        String provider,
        String emailAtLink,
        Instant linkedAt
) {
    public static FederatedIdentityResponse from(FederatedIdentity identity) {
        return new FederatedIdentityResponse(
                identity.getId(),
                identity.getProvider(),
                identity.getEmailAtLink(),
                identity.getLinkedAt()
        );
    }
}
