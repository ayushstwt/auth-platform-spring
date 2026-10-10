package com.iosocket.userservice.service;

import com.iosocket.userservice.dto.UserLogResponse;
import com.iosocket.userservice.entity.LogAction;
import com.iosocket.userservice.entity.LogSubAction;
import com.iosocket.userservice.entity.UserLog;
import com.iosocket.userservice.repository.UserLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private UserLogRepository userLogRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    @DisplayName("Should record activity and persist UserLog")
    void recordActivity() {
        UserLog savedLog = new UserLog(1L, LogAction.LOGIN, LogSubAction.SUCCESS);
        savedLog.setId(10L);

        when(userLogRepository.save(any(UserLog.class))).thenReturn(savedLog);

        UserLog result = auditService.recordActivity(1L, LogAction.LOGIN, LogSubAction.SUCCESS);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getAction()).isEqualTo(LogAction.LOGIN);
        assertThat(result.getSubAction()).isEqualTo(LogSubAction.SUCCESS);

        verify(userLogRepository).save(any(UserLog.class));
    }

    @Test
    @DisplayName("Should retrieve user logs with pagination")
    void getUserLogs() {
        UserLogResponse logDto = new UserLogResponse(1L, 10L, LogAction.LOGIN, LogSubAction.SUCCESS, Instant.now());
        Page<UserLogResponse> page = new PageImpl<>(List.of(logDto));

        when(userLogRepository.findResponsesByUserId(10L, PageRequest.of(0, 10))).thenReturn(page);

        Page<UserLogResponse> result = auditService.getUserLogs(10L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).userId()).isEqualTo(10L);
    }
}
