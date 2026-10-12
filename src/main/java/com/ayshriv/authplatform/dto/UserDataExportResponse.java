package com.ayshriv.authplatform.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record UserDataExportResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        boolean emailVerified,
        String phoneE164,
        boolean phoneVerified,
        String status,
        Instant createdOn,
        Instant lastLoginAt,
        Set<String> roles,
        List<FederatedIdentityResponse> identities,
        List<SessionResponse> activeSessions,
        List<UserLogResponse> recentActivity
) {
}
