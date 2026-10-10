package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.LogSubAction;
import com.ayshriv.authplatform.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;

    public TokenRevocationService(RefreshTokenRepository refreshTokenRepository,
                                  AuditService auditService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTokenReuse(Long userId, String familyId) {
        refreshTokenRepository.revokeByFamilyId(familyId, Instant.now());
        if (userId != null) {
            auditService.recordActivity(userId, LogAction.TOKEN_REUSE_DETECTED, LogSubAction.REVOKED);
        }
        log.warn("Revoked token family {} due to token reuse detection for user id={}", familyId, userId);
    }
}
