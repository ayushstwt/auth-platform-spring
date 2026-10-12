package com.ayshriv.authplatform.controller;

import com.ayshriv.authplatform.dto.ApiResponse;
import com.ayshriv.authplatform.dto.PageMetadata;
import com.ayshriv.authplatform.dto.UserLogResponse;
import com.ayshriv.authplatform.dto.UserResponse;
import com.ayshriv.authplatform.entity.LogAction;
import com.ayshriv.authplatform.entity.UserStatus;
import com.ayshriv.authplatform.security.SecurityUtils;
import com.ayshriv.authplatform.service.AuditService;
import com.ayshriv.authplatform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Privileged Administration and Audit Endpoints")
public class AdminController {

    private final UserService userService;
    private final AuditService auditService;

    public AdminController(UserService userService, AuditService auditService) {
        this.userService = userService;
        this.auditService = auditService;
    }

    @GetMapping("/users")
    @Operation(summary = "Search and list users with pagination and filters (Admin only)")
    public ResponseEntity<ApiResponse<List<UserResponse>>> searchUsers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<UserResponse> usersPage = userService.searchUsers(query, status, PageRequest.of(page, size, Sort.by("id").descending()));
        return ResponseEntity.ok(ApiResponse.success(
                "Users retrieved successfully",
                usersPage.getContent(),
                PageMetadata.from(usersPage)
        ));
    }

    @PostMapping("/users/{id}/lock")
    @Operation(summary = "Lock user account (Admin only)")
    public ResponseEntity<ApiResponse<UserResponse>> lockUser(
            @PathVariable Long id,
            @RequestParam(defaultValue = "60") long minutes) {
        Long adminId = SecurityUtils.getAuthenticatedUserId();
        UserResponse user = userService.lockUser(adminId, id, minutes);
        return ResponseEntity.ok(ApiResponse.success("User locked successfully", user));
    }

    @PostMapping("/users/{id}/unlock")
    @Operation(summary = "Unlock user account (Admin only)")
    public ResponseEntity<ApiResponse<UserResponse>> unlockUser(@PathVariable Long id) {
        Long adminId = SecurityUtils.getAuthenticatedUserId();
        UserResponse user = userService.unlockUser(adminId, id);
        return ResponseEntity.ok(ApiResponse.success("User unlocked successfully", user));
    }

    @PostMapping("/users/{id}/disable")
    @Operation(summary = "Disable user account permanently (Admin only)")
    public ResponseEntity<ApiResponse<UserResponse>> disableUser(@PathVariable Long id) {
        Long adminId = SecurityUtils.getAuthenticatedUserId();
        UserResponse user = userService.disableUser(adminId, id);
        return ResponseEntity.ok(ApiResponse.success("User disabled successfully", user));
    }

    @PostMapping("/users/{id}/force-logout")
    @Operation(summary = "Force logout user across all devices (Admin only)")
    public ResponseEntity<ApiResponse<Void>> forceLogout(@PathVariable Long id) {
        Long adminId = SecurityUtils.getAuthenticatedUserId();
        userService.forceLogoutUser(adminId, id);
        return ResponseEntity.ok(ApiResponse.success("Forced logout executed successfully", null));
    }

    @PostMapping("/users/{id}/force-password-reset")
    @Operation(summary = "Force password reset for user (Admin only)")
    public ResponseEntity<ApiResponse<Void>> forcePasswordReset(@PathVariable Long id) {
        Long adminId = SecurityUtils.getAuthenticatedUserId();
        userService.forcePasswordReset(adminId, id);
        return ResponseEntity.ok(ApiResponse.success("Password reset initiated successfully", null));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Query audit logs with filtering and pagination (Admin only)")
    public ResponseEntity<ApiResponse<List<UserLogResponse>>> queryAuditLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) LogAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<UserLogResponse> logsPage = auditService.queryAuditLogs(userId, action, startDate, endDate, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(
                "Audit logs retrieved successfully",
                logsPage.getContent(),
                PageMetadata.from(logsPage)
        ));
    }
}
