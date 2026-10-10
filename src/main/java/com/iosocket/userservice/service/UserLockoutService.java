package com.iosocket.userservice.service;

import com.iosocket.userservice.config.AppProperties;
import com.iosocket.userservice.entity.LogAction;
import com.iosocket.userservice.entity.LogSubAction;
import com.iosocket.userservice.entity.UserStatus;
import com.iosocket.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class UserLockoutService {

    private static final Logger log = LoggerFactory.getLogger(UserLockoutService.class);

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AppProperties appProperties;

    public UserLockoutService(UserRepository userRepository,
                              AuditService auditService,
                              AppProperties appProperties) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.appProperties = appProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            int failedAttempts = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(failedAttempts);

            int maxAttempts = appProperties.security().auth().maxFailedAttempts();
            if (failedAttempts >= maxAttempts) {
                long lockMinutes = appProperties.security().auth().lockoutDurationMinutes();
                user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(lockMinutes)));
                user.setStatus(UserStatus.LOCKED);
                auditService.recordActivity(user.getId(), LogAction.ACCOUNT_LOCK, LogSubAction.SUCCESS);
                log.warn("User id={} locked due to {} failed login attempts", user.getId(), failedAttempts);
            }

            userRepository.save(user);
            auditService.recordActivity(user.getId(), LogAction.LOGIN, LogSubAction.FAILURE);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetFailedLogins(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            if (user.getStatus() == UserStatus.LOCKED) {
                user.setStatus(UserStatus.ACTIVE);
            }
            userRepository.save(user);
        });
    }
}
