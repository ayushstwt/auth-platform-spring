package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.dto.ChangePasswordRequest;
import com.ayshriv.authplatform.dto.UpdateUserRequest;
import com.ayshriv.authplatform.dto.UserResponse;
import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.LogSubAction;
import com.ayshriv.authplatform.entity.RefreshToken;
import com.ayshriv.authplatform.entity.Role;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.entity.UserStatus;
import com.ayshriv.authplatform.exception.BadRequestException;
import com.ayshriv.authplatform.exception.ResourceNotFoundException;
import com.ayshriv.authplatform.repository.RefreshTokenRepository;
import com.ayshriv.authplatform.repository.RoleRepository;
import com.ayshriv.authplatform.repository.UserRepository;
import com.ayshriv.authplatform.security.JwtTokenService;
import com.ayshriv.authplatform.security.PasswordPolicyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;
    @Mock
    private AuditService auditService;
    @Mock
    private OtpService otpService;
    @Mock
    private EmailSender emailSender;
    @Mock
    private SessionManagementService sessionManagementService;
    @Mock
    private GoogleAuthService googleAuthService;

    private UserService userService;
    private User testUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                passwordPolicyValidator,
                auditService,
                otpService,
                emailSender,
                sessionManagementService,
                googleAuthService
        );

        testUser = new User();
        testUser.setId(10L);
        testUser.setEmail("user10@example.com");
        testUser.setPasswordHash("encodedOldPassword");
        testUser.setFirstName("OldFirst");
        testUser.setLastName("OldLast");
        testUser.setStatus(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("getCurrentUser returns user response")
    void getCurrentUserSuccess() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(testUser));

        UserResponse response = userService.getCurrentUser(10L);

        assertEquals("user10@example.com", response.email());
        assertEquals("OldFirst", response.firstName());
    }

    @Test
    @DisplayName("getCurrentUser throws ResourceNotFoundException if user missing")
    void getCurrentUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getCurrentUser(99L));
    }

    @Test
    @DisplayName("updateProfile updates first and last name")
    void updateProfileSuccess() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserResponse response = userService.updateProfile(10L, new UpdateUserRequest("NewFirst", "NewLast"));

        assertEquals("NewFirst", testUser.getFirstName());
        assertEquals("NewLast", testUser.getLastName());
        verify(auditService).recordActivity(eq(10L), eq(LogAction.ACCOUNT_UPDATE), eq(LogSubAction.SUCCESS));
    }

    @Test
    @DisplayName("changePassword throws when old password does not match")
    void changePasswordIncorrectOldPassword() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongOldPassword", "encodedOldPassword")).thenReturn(false);

        assertThrows(com.ayshriv.authplatform.exception.UnauthorizedException.class, () ->
                userService.changePassword(10L, new ChangePasswordRequest("wrongOldPassword", "NewSecretPass123!")));
    }

    @Test
    @DisplayName("changePassword updates password on valid old password")
    void changePasswordSuccess() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctOldPassword", "encodedOldPassword")).thenReturn(true);
        when(passwordEncoder.encode("NewSecretPass123!")).thenReturn("encodedNewPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        userService.changePassword(10L, new ChangePasswordRequest("correctOldPassword", "NewSecretPass123!"));

        assertEquals("encodedNewPassword", testUser.getPasswordHash());
        verify(refreshTokenRepository).revokeAllByUserId(eq(10L), any());
    }

    @Test
    @DisplayName("admin lockUser sets status to LOCKED and locks until duration")
    void adminLockUserSuccess() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserResponse response = userService.lockUser(1L, 10L, 30);

        assertEquals(UserStatus.LOCKED, testUser.getStatus());
        assertNotNull(testUser.getLockedUntil());
        verify(refreshTokenRepository).revokeAllByUserId(eq(10L), any());
    }

    @Test
    @DisplayName("admin unlockUser resets locked status and count")
    void adminUnlockUserSuccess() {
        testUser.setStatus(UserStatus.LOCKED);
        testUser.setLockedUntil(Instant.now().plusSeconds(3600));
        testUser.setFailedLoginCount(5);

        when(userRepository.findById(10L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserResponse response = userService.unlockUser(1L, 10L);

        assertEquals(UserStatus.ACTIVE, testUser.getStatus());
        assertEquals(0, testUser.getFailedLoginCount());
    }

    @Test
    @DisplayName("admin disableUser sets status to DISABLED")
    void adminDisableUserSuccess() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserResponse response = userService.disableUser(1L, 10L);

        assertEquals(UserStatus.DISABLED, testUser.getStatus());
        verify(refreshTokenRepository).revokeAllByUserId(eq(10L), any());
    }

    @Test
    @DisplayName("admin forcePasswordReset clears password and generates OTP")
    void adminForcePasswordResetSuccess() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(testUser));
        when(otpService.generateAndSaveOtp(any(), any(), any(), eq("user10@example.com"), eq(null))).thenReturn("654321");

        userService.forcePasswordReset(1L, 10L);

        verify(emailSender).sendPasswordResetEmail(eq("user10@example.com"), eq("OldFirst"), eq("654321"));
        verify(refreshTokenRepository).revokeAllByUserId(eq(10L), any());
    }

    @Test
    @DisplayName("hardPurgeRetentionUsers delegates to repository")
    void hardPurgeRetentionUsers() {
        when(userRepository.hardPurgeUsersBefore(any(Instant.class))).thenReturn(5);

        int purged = userService.hardPurgeRetentionUsers(30);

        assertEquals(5, purged);
    }
}
