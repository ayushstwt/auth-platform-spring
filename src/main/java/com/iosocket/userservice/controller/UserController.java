package com.iosocket.userservice.controller;

import com.iosocket.userservice.dto.ApiResponse;
import com.iosocket.userservice.dto.ChangePasswordRequest;
import com.iosocket.userservice.dto.UpdateUserRequest;
import com.iosocket.userservice.dto.UserResponse;
import com.iosocket.userservice.security.SecurityUtils;
import com.iosocket.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User Profile & Password Management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile() {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        UserResponse response = userService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully", response));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user profile information")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(@Valid @RequestBody UpdateUserRequest request) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        UserResponse response = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("User profile updated successfully", response));
    }

    @PostMapping("/me/change-password")
    @Operation(summary = "Change password for authenticated user (revokes other active sessions)")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        userService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }
}
