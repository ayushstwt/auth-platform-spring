package com.ayshriv.authplatform.dto;

import java.time.Instant;

public record SessionResponse(
        String familyId,
        String createdIp,
        String userAgent,
        String deviceName,
        Instant createdOn,
        Instant lastUsedAt,
        Instant expiresAt,
        boolean currentSession
) {
}
