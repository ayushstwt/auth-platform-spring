package com.iosocket.userservice.repository;

import com.iosocket.userservice.dto.UserLogResponse;
import com.iosocket.userservice.entity.LogAction;
import com.iosocket.userservice.entity.LogSubAction;
import com.iosocket.userservice.entity.UserLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserLogRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserLogRepository userLogRepository;

    @Test
    @DisplayName("Should persist and retrieve user logs ordered by createdOn desc")
    void persistAndQueryUserLogs() {
        UserLog log1 = new UserLog(100L, LogAction.LOGIN, LogSubAction.SUCCESS);
        log1.setCreatedOn(Instant.now().minusSeconds(60));

        UserLog log2 = new UserLog(100L, LogAction.LOGOUT, LogSubAction.SUCCESS);
        log2.setCreatedOn(Instant.now());

        UserLog logOther = new UserLog(200L, LogAction.LOGIN, LogSubAction.SUCCESS);

        entityManager.persistAndFlush(log1);
        entityManager.persistAndFlush(log2);
        entityManager.persistAndFlush(logOther);

        Page<UserLogResponse> results = userLogRepository.findResponsesByUserId(100L, PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(2);
        assertThat(results.getContent()).extracting(UserLogResponse::action)
                .containsExactly(LogAction.LOGOUT, LogAction.LOGIN);
    }

    @Test
    @DisplayName("Should filter out soft-deleted user logs automatically")
    void filterSoftDeletedUserLogs() {
        UserLog activeLog = new UserLog(300L, LogAction.REGISTER, LogSubAction.SUCCESS);
        UserLog deletedLog = new UserLog(300L, LogAction.LOGIN, LogSubAction.FAILURE);

        entityManager.persistAndFlush(activeLog);
        entityManager.persistAndFlush(deletedLog);

        deletedLog.setDeleted(true);
        entityManager.persistAndFlush(deletedLog);

        entityManager.clear();

        Page<UserLogResponse> results = userLogRepository.findResponsesByUserId(300L, PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().get(0).action()).isEqualTo(LogAction.REGISTER);
    }
}
