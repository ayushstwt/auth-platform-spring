package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.dto.PageMetadata;
import com.ayshriv.authplatform.dto.UserLogResponse;
import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.LogSubAction;
import com.ayshriv.authplatform.entity.UserLog;
import com.ayshriv.authplatform.repository.UserLogRepository;
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
        return recordActivity(userId, action, subAction, null);
    }

    @Transactional
    public UserLog recordActivity(Long userId, LogAction action, LogSubAction subAction, String ipAddress) {
        log.info("Audit log: userId={} action={} subAction={} ip={}", userId, action, subAction, ipAddress != null ? ipAddress : "n/a");
        UserLog userLog = new UserLog(userId, action, subAction, ipAddress);
        return userLogRepository.save(userLog);
    }

    @Transactional(readOnly = true)
    public Page<UserLogResponse> getUserLogs(Long userId, Pageable pageable) {
        return userLogRepository.findResponsesByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<UserLogResponse> queryAuditLogs(Long userId, LogAction action, java.time.Instant startDate, java.time.Instant endDate, Pageable pageable) {
        Page<UserLog> logs = userLogRepository.searchLogs(userId, action, startDate, endDate, pageable);
        return logs.map(l -> new UserLogResponse(l.getId(), l.getUserId(), l.getAction(), l.getSubAction(), l.getIpAddress(), l.getCreatedOn()));
    }
}
