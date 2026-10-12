package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.constants.ErrorMessages;
import com.ayshriv.authplatform.dto.ChangePasswordRequest;
import com.ayshriv.authplatform.dto.EmailChangeConfirmRequest;
import com.ayshriv.authplatform.dto.EmailChangeRequest;
import com.ayshriv.authplatform.dto.FederatedIdentityResponse;
import com.ayshriv.authplatform.dto.SessionResponse;
import com.ayshriv.authplatform.dto.UpdateUserRequest;
import com.ayshriv.authplatform.dto.UserDataExportResponse;
import com.ayshriv.authplatform.dto.UserLogResponse;
import com.ayshriv.authplatform.dto.UserResponse;
import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.LogSubAction;
import com.ayshriv.authplatform.entity.OtpChannel;
import com.ayshriv.authplatform.entity.OtpPurpose;
import com.ayshriv.authplatform.entity.Role;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.entity.UserStatus;
import com.ayshriv.authplatform.exception.BadRequestException;
import com.ayshriv.authplatform.exception.ResourceAlreadyExistException;
import com.ayshriv.authplatform.exception.ResourceNotFoundException;
import com.ayshriv.authplatform.exception.UnauthorizedException;
import com.ayshriv.authplatform.repository.RefreshTokenRepository;
import com.ayshriv.authplatform.repository.UserRepository;
import com.ayshriv.authplatform.security.PasswordPolicyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuditService auditService;
    private final OtpService otpService;
    private final EmailSender emailSender;
    private final SessionManagementService sessionManagementService;
    private final GoogleAuthService googleAuthService;

    public UserService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicyValidator passwordPolicyValidator,
                       AuditService auditService,
                       OtpService otpService,
                       EmailSender emailSender,
                       SessionManagementService sessionManagementService,
                       GoogleAuthService googleAuthService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.auditService = auditService;
        this.otpService = otpService;
        this.emailSender = emailSender;
        this.sessionManagementService = sessionManagementService;
        this.googleAuthService = googleAuthService;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        User updated = userRepository.save(user);

        auditService.recordActivity(userId, LogAction.ACCOUNT_UPDATE, LogSubAction.SUCCESS);
        log.info("Profile updated for user id={}", userId);

        return UserResponse.from(updated);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.recordActivity(userId, LogAction.PASSWORD_CHANGE, LogSubAction.FAILURE);
            throw new UnauthorizedException(ErrorMessages.E012);
        }

        passwordPolicyValidator.validate(request.newPassword());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        user.setHasPassword(true);
        userRepository.save(user);

        // Revoke other active sessions for security
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());

        auditService.recordActivity(userId, LogAction.PASSWORD_CHANGE, LogSubAction.SUCCESS);
        log.info("Password changed successfully for user id={}", userId);
    }

    @Transactional
    public void requestEmailChange(Long userId, EmailChangeRequest request, String clientIp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        String newEmail = request.newEmail().trim().toLowerCase();
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException("New email must be different from current email");
        }
        if (userRepository.existsByEmail(newEmail)) {
            throw new ResourceAlreadyExistException(ErrorMessages.E003);
        }

        String rawOtp = otpService.generateAndSaveOtp(user, OtpChannel.EMAIL, OtpPurpose.EMAIL_CHANGE, newEmail, clientIp);
        emailSender.sendVerificationEmail(newEmail, user.getFirstName(), rawOtp);
        auditService.recordActivity(userId, LogAction.EMAIL_CHANGE, LogSubAction.INITIATED, clientIp);
        log.info("Email change verification code dispatched for user id={}", userId);
    }

    @Transactional
    public UserResponse confirmEmailChange(Long userId, EmailChangeConfirmRequest request, String clientIp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        String newEmail = request.newEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(newEmail)) {
            throw new ResourceAlreadyExistException(ErrorMessages.E003);
        }

        otpService.verifyOtp(newEmail, OtpPurpose.EMAIL_CHANGE, request.code(), clientIp);

        user.setEmail(newEmail);
        user.setEmailVerified(true);
        User saved = userRepository.save(user);

        auditService.recordActivity(userId, LogAction.EMAIL_CHANGE, LogSubAction.SUCCESS, clientIp);
        log.info("User id={} successfully updated email address", userId);
        return UserResponse.from(saved);
    }

    @Transactional
    public void softDeleteUser(Long userId, String clientIp) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        user.setDeleted(true);
        user.setActive(false);
        user.setDeletedAt(Instant.now());
        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);

        // Revoke all sessions
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());

        auditService.recordActivity(userId, LogAction.ACCOUNT_DELETE, LogSubAction.SUCCESS, clientIp);
        log.info("User id={} soft-deleted account", userId);
    }

    @Transactional(readOnly = true)
    public UserDataExportResponse exportUserData(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, userId));

        List<FederatedIdentityResponse> identities = googleAuthService.getIdentities(userId);
        List<SessionResponse> sessions = sessionManagementService.getActiveSessions(userId, null);
        Page<UserLogResponse> recentActivity = auditService.getUserLogs(userId, PageRequest.of(0, 50));
        Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());

        auditService.recordActivity(userId, LogAction.DATA_EXPORT, LogSubAction.SUCCESS);

        return new UserDataExportResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isEmailVerified(),
                user.getPhoneE164(),
                user.isPhoneVerified(),
                user.getStatus().name(),
                user.getCreatedOn(),
                user.getLastLoginAt(),
                roleNames,
                identities,
                sessions,
                recentActivity.getContent()
        );
    }

    // --- Admin Operations ---

    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(String query, UserStatus status, Pageable pageable) {
        return userRepository.searchUsers(query, status, pageable).map(UserResponse::from);
    }

    @Transactional
    public UserResponse lockUser(Long adminUserId, Long targetUserId, long lockMinutes) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, targetUserId));
        user.setStatus(UserStatus.LOCKED);
        user.setLockedUntil(Instant.now().plusSeconds(lockMinutes * 60));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(targetUserId, Instant.now());
        auditService.recordActivity(targetUserId, LogAction.ACCOUNT_LOCK, LogSubAction.SUCCESS);
        log.info("Admin {} locked user {}", adminUserId, targetUserId);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse unlockUser(Long adminUserId, Long targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, targetUserId));
        user.setStatus(UserStatus.ACTIVE);
        user.setLockedUntil(null);
        user.setFailedLoginCount(0);
        userRepository.save(user);
        auditService.recordActivity(targetUserId, LogAction.ACCOUNT_UNLOCK, LogSubAction.SUCCESS);
        log.info("Admin {} unlocked user {}", adminUserId, targetUserId);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse disableUser(Long adminUserId, Long targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, targetUserId));
        user.setStatus(UserStatus.DISABLED);
        user.setActive(false);
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(targetUserId, Instant.now());
        auditService.recordActivity(targetUserId, LogAction.ACCOUNT_DISABLE, LogSubAction.SUCCESS);
        log.info("Admin {} disabled user {}", adminUserId, targetUserId);
        return UserResponse.from(user);
    }

    @Transactional
    public void forceLogoutUser(Long adminUserId, Long targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, targetUserId));
        refreshTokenRepository.revokeAllByUserId(targetUserId, Instant.now());
        auditService.recordActivity(targetUserId, LogAction.SESSION_REVOKED, LogSubAction.REVOKED);
        log.info("Admin {} forced logout for user {}", adminUserId, targetUserId);
    }

    @Transactional
    public void forcePasswordReset(Long adminUserId, Long targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.E002, targetUserId));
        refreshTokenRepository.revokeAllByUserId(targetUserId, Instant.now());
        String otp = otpService.generateAndSaveOtp(user, OtpChannel.EMAIL, OtpPurpose.PASSWORD_RESET, user.getEmail(), null);
        emailSender.sendPasswordResetEmail(user.getEmail(), user.getFirstName(), otp);
        auditService.recordActivity(targetUserId, LogAction.PASSWORD_RESET, LogSubAction.INITIATED);
        log.info("Admin {} forced password reset for user {}", adminUserId, targetUserId);
    }

    @Transactional
    public int hardPurgeRetentionUsers(int retentionDays) {
        Instant threshold = Instant.now().minusSeconds((long) retentionDays * 84600);
        int purged = userRepository.hardPurgeUsersBefore(threshold);
        log.info("Purged {} soft-deleted accounts older than {} days", purged, retentionDays);
        return purged;
    }
}
