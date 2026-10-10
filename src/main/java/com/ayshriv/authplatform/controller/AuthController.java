package com.ayshriv.authplatform.controller;

import com.ayshriv.authplatform.dto.ApiResponse;
import com.ayshriv.authplatform.dto.AuthResponse;
import com.ayshriv.authplatform.dto.LoginRequest;
import com.ayshriv.authplatform.dto.LogoutRequest;
import com.ayshriv.authplatform.dto.RefreshTokenRequest;
import com.ayshriv.authplatform.dto.RegisterRequest;
import com.ayshriv.authplatform.dto.UserResponse;
import com.ayshriv.authplatform.security.SecurityUtils;
import com.ayshriv.authplatform.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User Registration, Login, and Token Management")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user account with email and password")
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest) {
        UserResponse response = authService.register(
                request,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")
        );
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + response.id()))
                .body(ApiResponse.success("User registered successfully", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password to obtain tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        AuthResponse response = authService.login(
                request,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")
        );
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue a new access token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest servletRequest) {
        AuthResponse response = authService.refreshToken(
                request,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")
        );
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke current refresh token family")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) LogoutRequest request) {
        if (request != null && request.refreshToken() != null) {
            authService.logout(request.refreshToken());
        }
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Revoke all active sessions and refresh tokens for authenticated user")
    public ResponseEntity<ApiResponse<Void>> logoutAll() {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        authService.logoutAll(userId);
        return ResponseEntity.ok(ApiResponse.success("All sessions logged out successfully", null));
    }
}
