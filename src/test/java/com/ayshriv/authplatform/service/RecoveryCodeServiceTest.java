package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.entity.UserRecoveryCode;
import com.ayshriv.authplatform.repository.UserRecoveryCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RecoveryCodeServiceTest {

    private UserRecoveryCodeRepository recoveryCodeRepository;
    private RecoveryCodeService recoveryCodeService;
    private User testUser;

    @BeforeEach
    void setUp() {
        this.recoveryCodeRepository = mock(UserRecoveryCodeRepository.class);
        AppProperties.OtpProperties otpProps = new AppProperties.OtpProperties("test-pepper", 5, 5, 60, 5);
        AppProperties appProperties = new AppProperties(null, otpProps, null, null, null);

        this.recoveryCodeService = new RecoveryCodeService(recoveryCodeRepository, appProperties);
        this.testUser = new User();
        this.testUser.setId(10L);
    }

    @Test
    @DisplayName("Should generate exactly 10 formatted recovery codes and persist their hashes")
    void generateTenRecoveryCodes() {
        List<String> codes = recoveryCodeService.generateAndSaveRecoveryCodes(testUser);

        assertThat(codes).hasSize(10);
        for (String code : codes) {
            assertThat(code).matches("^[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}$");
        }

        verify(recoveryCodeRepository).deleteAllByUserId(10L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserRecoveryCode>> captor = ArgumentCaptor.forClass(List.class);
        verify(recoveryCodeRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(10);
    }

    @Test
    @DisplayName("Should validate and consume a valid unconsumed recovery code")
    void validateAndConsumeCode() {
        String testCode = "ABCD-EFGH";
        UserRecoveryCode entity = new UserRecoveryCode(testUser, "dummy-hash");
        when(recoveryCodeRepository.findByUserIdAndCodeHash(eq(10L), any()))
                .thenReturn(Optional.of(entity));

        boolean result = recoveryCodeService.validateAndConsumeRecoveryCode(testUser, testCode);

        assertThat(result).isTrue();
        assertThat(entity.isConsumed()).isTrue();
        verify(recoveryCodeRepository).save(entity);
    }

    @Test
    @DisplayName("Should reject an already consumed recovery code (single-use enforcement)")
    void rejectAlreadyConsumedCode() {
        String testCode = "ABCD-EFGH";
        UserRecoveryCode entity = new UserRecoveryCode(testUser, "dummy-hash");
        entity.setConsumedAt(java.time.Instant.now());
        when(recoveryCodeRepository.findByUserIdAndCodeHash(eq(10L), any()))
                .thenReturn(Optional.of(entity));

        boolean result = recoveryCodeService.validateAndConsumeRecoveryCode(testUser, testCode);

        assertThat(result).isFalse();
        verify(recoveryCodeRepository, never()).save(entity);
    }
}
