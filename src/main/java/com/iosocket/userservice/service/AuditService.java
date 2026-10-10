package com.iosocket.userservice.service;

import com.iosocket.userservice.dto.PageMetadata;
import com.iosocket.userservice.dto.UserLogResponse;
import com.iosocket.userservice.entity.LogAction;
import com.iosocket.userservice.entity.LogSubAction;
import com.iosocket.userservice.entity.UserLog;
import com.iosocket.userservice.repository.UserLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final UserLogRepository userLogRepository;

    public AuditService(UserLogRepository userLogRepository) {
        this.userLogRepository = userLogRepository;
    }

    @Transactional
    public UserLog recordActivity(Long userId, LogAction action, LogSubAction subAction) {
        log.info("Audit log: userId={} action={} subAction={}", userId, action, subAction);
        UserLog userLog = new UserLog(userId, action, subAction);
        return userLogRepository.save(userLog);
    }

    @Transactional(readOnly = true)
    public Page<UserLogResponse> getUserLogs(Long userId, Pageable pageable) {
        return userLogRepository.findResponsesByUserId(userId, pageable);
    }
}
