package com.iosocket.userservice.service;

import com.iosocket.userservice.constants.ErrorMessages;
import com.iosocket.userservice.dto.ChangePasswordRequest;
import com.iosocket.userservice.dto.UpdateUserRequest;
import com.iosocket.userservice.dto.UserResponse;
import com.iosocket.userservice.entity.LogAction;
import com.iosocket.userservice.entity.LogSubAction;
import com.iosocket.userservice.entity.User;
import com.iosocket.userservice.exception.ResourceNotFoundException;
import com.iosocket.userservice.exception.UnauthorizedException;
import com.iosocket.userservice.repository.RefreshTokenRepository;
import com.iosocket.userservice.repository.UserRepository;
import com.iosocket.userservice.security.PasswordPolicyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicyValidator passwordPolicyValidator,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.auditService = auditService;
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
        userRepository.save(user);

        // Revoke other active sessions for security
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());

        auditService.recordActivity(userId, LogAction.PASSWORD_CHANGE, LogSubAction.SUCCESS);
        log.info("Password changed successfully for user id={}", userId);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }
}
