package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.dto.SessionResponse;
import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.LogSubAction;
import com.ayshriv.authplatform.entity.RefreshToken;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SessionManagementService {

    private static final Logger log = LoggerFactory.getLogger(SessionManagementService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailSender emailSender;
    private final AuditService auditService;

    public SessionManagementService(RefreshTokenRepository refreshTokenRepository,
                                  EmailSender emailSender,
                                  AuditService auditService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailSender = emailSender;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getActiveSessions(Long userId, String currentFamilyId) {
        List<RefreshToken> tokens = refreshTokenRepository.findActiveSessionsByUserId(userId, Instant.now());
        // Deduplicate by familyId, keeping latest token
        Map<String, RefreshToken> familyMap = new LinkedHashMap<>();
        for (RefreshToken token : tokens) {
            familyMap.putIfAbsent(token.getFamilyId(), token);
        }

        List<SessionResponse> sessions = new ArrayList<>();
        for (RefreshToken token : familyMap.values()) {
            boolean isCurrent = currentFamilyId != null && currentFamilyId.equals(token.getFamilyId());
            sessions.add(new SessionResponse(
                    token.getFamilyId(),
                    token.getCreatedIp(),
                    token.getUserAgent(),
                    token.getDeviceName() != null ? token.getDeviceName() : parseSimpleDevice(token.getUserAgent()),
                    token.getCreatedOn(),
                    token.getLastUsedAt() != null ? token.getLastUsedAt() : token.getCreatedOn(),
                    token.getExpiresAt(),
                    isCurrent
            ));
        }
        return sessions;
    }

    @Transactional
    public void revokeSession(Long userId, String familyId) {
        refreshTokenRepository.revokeByFamilyId(familyId, Instant.now());
        auditService.recordActivity(userId, LogAction.SESSION_REVOKED, LogSubAction.REVOKED);
        log.info("Revoked session family {} for user id={}", familyId, userId);
    }

    @Transactional
    public void revokeOtherSessions(Long userId, String keepFamilyId) {
        refreshTokenRepository.revokeAllByUserIdExceptFamily(userId, keepFamilyId, Instant.now());
        auditService.recordActivity(userId, LogAction.SESSION_REVOKED, LogSubAction.REVOKED);
        log.info("Revoked all other sessions except {} for user id={}", keepFamilyId, userId);
    }

    @Transactional
    public void checkAndAlertNewDeviceLogin(User user, String clientIp, String userAgent) {
        List<RefreshToken> existingTokens = refreshTokenRepository.findActiveSessionsByUserId(user.getId(), Instant.now());
        boolean knownDevice = existingTokens.stream()
                .anyMatch(t -> (userAgent != null && userAgent.equalsIgnoreCase(t.getUserAgent())) ||
                               (clientIp != null && clientIp.equalsIgnoreCase(t.getCreatedIp())));

        if (!knownDevice && !existingTokens.isEmpty()) {
            try {
                String body = "We detected a login from a new device/location: "
                        + "IP: " + (clientIp != null ? clientIp : "Unknown") + ", "
                        + "Browser/Client: " + (userAgent != null ? userAgent : "Unknown") + ", "
                        + "Time: " + Instant.now();
                emailSender.sendSecurityAlertEmail(user.getEmail(), user.getFirstName(), body);
                log.info("Dispatched new-device security alert email to user id={}", user.getId());
            } catch (Exception ex) {
                log.warn("Failed to send new-device login notification to user id={}: {}", user.getId(), ex.getMessage());
            }
        }
    }

    private String parseSimpleDevice(String userAgent) {
        if (userAgent == null) {
            return "Unknown Device";
        }
        if (userAgent.contains("Postman")) return "Postman Runtime";
        if (userAgent.contains("Android")) return "Android Device";
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) return "iOS Device";
        if (userAgent.contains("Macintosh")) return "macOS";
        if (userAgent.contains("Windows")) return "Windows PC";
        if (userAgent.contains("Linux")) return "Linux Device";
        return "Web Browser";
    }
}
