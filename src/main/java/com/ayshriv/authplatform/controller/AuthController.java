package com.ayshriv.authplatform.controller;

import com.ayshriv.authplatform.dto.ApiResponse;
import com.ayshriv.authplatform.dto.AuthResponse;
import com.ayshriv.authplatform.dto.EmailOtpConfirmRequest;
import com.ayshriv.authplatform.dto.EmailOtpSendRequest;
import com.ayshriv.authplatform.dto.ForgotPasswordRequest;
import com.ayshriv.authplatform.dto.LoginRequest;
import com.ayshriv.authplatform.dto.LogoutRequest;
import com.ayshriv.authplatform.dto.RefreshTokenRequest;
import com.ayshriv.authplatform.dto.RegisterRequest;
import com.ayshriv.authplatform.dto.ResetPasswordRequest;
import com.ayshriv.authplatform.dto.UserResponse;
import com.ayshriv.authplatform.dto.VerifyEmailConfirmRequest;
import com.ayshriv.authplatform.security.SecurityUtils;
import com.ayshriv.authplatform.service.AuthService;
import com.ayshriv.authplatform.service.EmailAuthService;
import com.ayshriv.authplatform.service.PhoneAuthService;
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
    private final EmailAuthService emailAuthService;
    private final PhoneAuthService phoneAuthService;
    private final com.ayshriv.authplatform.service.GoogleAuthService googleAuthService;

    public AuthController(AuthService authService,
                          EmailAuthService emailAuthService,
                          PhoneAuthService phoneAuthService,
                          com.ayshriv.authplatform.service.GoogleAuthService googleAuthService) {
        this.authService = authService;
        this.emailAuthService = emailAuthService;
        this.phoneAuthService = phoneAuthService;
        this.googleAuthService = googleAuthService;
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
    @Operation(summary = "Authenticate with email and password to obtain tokens or MFA challenge")
    public ResponseEntity<ApiResponse<com.ayshriv.authplatform.dto.LoginResult>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        com.ayshriv.authplatform.dto.LoginResult response = authService.login(
                request,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")
        );
        String msg = response.mfaRequired() ? "MFA verification required" : "Authentication successful";
        return ResponseEntity.ok(ApiResponse.success(msg, response));
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

    @PostMapping("/email/verify/send")
    @Operation(summary = "Send verification code to authenticated user's email")
    public ResponseEntity<ApiResponse<Void>> sendEmailVerification(HttpServletRequest servletRequest) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        emailAuthService.sendEmailVerificationOtp(userId, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Verification code sent to your email", null));
    }

    @PostMapping("/email/verify/confirm")
    @Operation(summary = "Confirm email verification using 6-digit code")
    public ResponseEntity<ApiResponse<Void>> confirmEmailVerification(
            @Valid @RequestBody VerifyEmailConfirmRequest request,
            HttpServletRequest servletRequest) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        emailAuthService.confirmEmailVerification(userId, request, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", null));
    }

    @PostMapping("/login/email-otp/send")
    @Operation(summary = "Request one-time login code via email (passwordless)")
    public ResponseEntity<ApiResponse<Void>> sendLoginOtp(
            @Valid @RequestBody EmailOtpSendRequest request,
            HttpServletRequest servletRequest) {
        emailAuthService.sendLoginOtp(request, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("If the account exists, a one-time passcode has been sent", null));
    }

    @PostMapping("/login/email-otp/confirm")
    @Operation(summary = "Confirm one-time login code and obtain JWT authentication tokens or MFA challenge")
    public ResponseEntity<ApiResponse<com.ayshriv.authplatform.dto.LoginResult>> confirmLoginOtp(
            @Valid @RequestBody EmailOtpConfirmRequest request,
            HttpServletRequest servletRequest) {
        com.ayshriv.authplatform.dto.LoginResult response = emailAuthService.confirmLoginOtp(
                request,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")
        );
        String msg = response.mfaRequired() ? "MFA verification required" : "Authentication successful";
        return ResponseEntity.ok(ApiResponse.success(msg, response));
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Request password reset code via email")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest servletRequest) {
        emailAuthService.forgotPassword(request, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("If the account exists, password reset instructions have been sent", null));
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Reset password using OTP code")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest) {
        emailAuthService.resetPassword(request, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully. Please log in with your new password", null));
    }

    @PostMapping("/login/phone-otp/send")
    @Operation(summary = "Request one-time login code via SMS")
    public ResponseEntity<ApiResponse<Void>> sendPhoneLoginOtp(
            @Valid @RequestBody com.ayshriv.authplatform.dto.PhoneLoginSendRequest request,
            HttpServletRequest servletRequest) {
        phoneAuthService.sendLoginPhoneOtp(request, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("If the account exists and phone is verified, a one-time passcode has been sent", null));
    }

    @PostMapping("/login/phone-otp/confirm")
    @Operation(summary = "Confirm one-time login SMS code and obtain JWT authentication tokens or MFA challenge")
    public ResponseEntity<ApiResponse<com.ayshriv.authplatform.dto.LoginResult>> confirmPhoneLoginOtp(
            @Valid @RequestBody com.ayshriv.authplatform.dto.PhoneLoginConfirmRequest request,
            HttpServletRequest servletRequest) {
        com.ayshriv.authplatform.dto.LoginResult response = phoneAuthService.confirmPhoneLoginOtp(
                request,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")
        );
        String msg = response.mfaRequired() ? "MFA verification required" : "Authentication successful";
        return ResponseEntity.ok(ApiResponse.success(msg, response));
    }

    @PostMapping("/google")
    @Operation(summary = "Sign in or sign up using verified Google OIDC ID token")
    public ResponseEntity<ApiResponse<com.ayshriv.authplatform.dto.LoginResult>> googleLogin(
            @Valid @RequestBody com.ayshriv.authplatform.dto.GoogleTokenLoginRequest request,
            HttpServletRequest servletRequest) {
        com.ayshriv.authplatform.dto.LoginResult response = googleAuthService.loginWithIdToken(
                request.idToken(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")
        );
        String msg = response.mfaRequired() ? "MFA verification required" : "Google authentication successful";
        return ResponseEntity.ok(ApiResponse.success(msg, response));
    }

    @PostMapping("/google/exchange")
    @Operation(summary = "Exchange one-time authorization code for JWT authentication tokens")
    public ResponseEntity<ApiResponse<com.ayshriv.authplatform.dto.LoginResult>> googleExchange(
            @Valid @RequestBody com.ayshriv.authplatform.dto.GoogleExchangeRequest request,
            HttpServletRequest servletRequest) {
        com.ayshriv.authplatform.dto.LoginResult response = googleAuthService.exchangeCode(
                request.exchangeCode(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")
        );
        String msg = response.mfaRequired() ? "MFA verification required" : "Authentication successful";
        return ResponseEntity.ok(ApiResponse.success(msg, response));
    }
}

