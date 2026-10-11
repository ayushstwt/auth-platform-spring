package com.ayshriv.authplatform.controller;

import com.ayshriv.authplatform.dto.ApiResponse;
import com.ayshriv.authplatform.dto.ChangePasswordRequest;
import com.ayshriv.authplatform.dto.UpdateUserRequest;
import com.ayshriv.authplatform.dto.UserResponse;
import com.ayshriv.authplatform.security.SecurityUtils;
import com.ayshriv.authplatform.service.PhoneAuthService;
import com.ayshriv.authplatform.service.UserService;
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
    private final PhoneAuthService phoneAuthService;
    private final com.ayshriv.authplatform.service.GoogleAuthService googleAuthService;
    private final com.ayshriv.authplatform.service.MfaService mfaService;

    public UserController(UserService userService,
                          PhoneAuthService phoneAuthService,
                          com.ayshriv.authplatform.service.GoogleAuthService googleAuthService,
                          com.ayshriv.authplatform.service.MfaService mfaService) {
        this.userService = userService;
        this.phoneAuthService = phoneAuthService;
        this.googleAuthService = googleAuthService;
        this.mfaService = mfaService;
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
    @com.ayshriv.authplatform.security.RequiresRecentMfa
    @Operation(summary = "Change password for authenticated user (revokes other active sessions, requires recent login/MFA)")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        userService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    @PostMapping("/me/phone/send-otp")
    @com.ayshriv.authplatform.security.RequiresRecentMfa
    @Operation(summary = "Send OTP to add or update phone number (requires recent login if phone already verified)")
    public ResponseEntity<ApiResponse<Void>> sendPhoneOtp(
            @Valid @RequestBody com.ayshriv.authplatform.dto.PhoneOtpSendRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        phoneAuthService.sendPhoneVerificationOtp(userId, request, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Verification code sent to phone number", null));
    }

    @PostMapping("/me/phone/verify")
    @Operation(summary = "Verify phone number using SMS OTP code")
    public ResponseEntity<ApiResponse<UserResponse>> verifyPhone(
            @Valid @RequestBody com.ayshriv.authplatform.dto.PhoneVerifyRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        UserResponse response = phoneAuthService.verifyUserPhone(userId, request, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("Phone number verified successfully", response));
    }

    @GetMapping("/me/identities")
    @Operation(summary = "List all linked third-party federated identities")
    public ResponseEntity<ApiResponse<java.util.List<com.ayshriv.authplatform.dto.FederatedIdentityResponse>>> getIdentities() {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        var identities = googleAuthService.getIdentities(userId);
        return ResponseEntity.ok(ApiResponse.success("Federated identities retrieved successfully", identities));
    }

    @PostMapping("/me/identities/google/link")
    @Operation(summary = "Link Google account to existing authenticated user")
    public ResponseEntity<ApiResponse<com.ayshriv.authplatform.dto.FederatedIdentityResponse>> linkGoogle(
            @Valid @RequestBody com.ayshriv.authplatform.dto.GoogleTokenLoginRequest request) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        var response = googleAuthService.linkGoogleAccount(userId, request.idToken());
        return ResponseEntity.ok(ApiResponse.success("Google account linked successfully", response));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/me/identities/google")
    @Operation(summary = "Unlink Google account (blocked if no other login method remains)")
    public ResponseEntity<ApiResponse<Void>> unlinkGoogle() {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        googleAuthService.unlinkGoogleAccount(userId);
        return ResponseEntity.ok(ApiResponse.success("Google account unlinked successfully", null));
    }

    @PostMapping("/me/mfa/totp/setup")
    @com.ayshriv.authplatform.security.RequiresRecentMfa
    @Operation(summary = "Initialize TOTP MFA setup (returns secret and otpauth URI)")
    public ResponseEntity<ApiResponse<com.ayshriv.authplatform.dto.TotpSetupResponse>> setupTotp() {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        com.ayshriv.authplatform.dto.TotpSetupResponse response = mfaService.setupTotp(userId);
        return ResponseEntity.ok(ApiResponse.success("TOTP setup initiated", response));
    }

    @PostMapping("/me/mfa/totp/confirm")
    @com.ayshriv.authplatform.security.RequiresRecentMfa
    @Operation(summary = "Confirm TOTP code and enable MFA (returns 10 single-use recovery codes)")
    public ResponseEntity<ApiResponse<com.ayshriv.authplatform.dto.TotpConfirmResponse>> confirmTotp(
            @Valid @RequestBody com.ayshriv.authplatform.dto.TotpConfirmRequest request) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        com.ayshriv.authplatform.dto.TotpConfirmResponse response = mfaService.confirmTotp(userId, request);
        return ResponseEntity.ok(ApiResponse.success("MFA successfully enabled", response));
    }

    @PostMapping("/me/mfa/disable")
    @com.ayshriv.authplatform.security.RequiresRecentMfa
    @Operation(summary = "Disable MFA using password or valid verification code")
    public ResponseEntity<ApiResponse<Void>> disableMfa(
            @RequestBody com.ayshriv.authplatform.dto.DisableMfaRequest request) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        mfaService.disableMfa(userId, request);
        return ResponseEntity.ok(ApiResponse.success("MFA successfully disabled", null));
    }

    @PostMapping("/me/mfa/recovery-codes/regenerate")
    @com.ayshriv.authplatform.security.RequiresRecentMfa
    @Operation(summary = "Regenerate 10 emergency single-use recovery codes")
    public ResponseEntity<ApiResponse<com.ayshriv.authplatform.dto.RecoveryCodesResponse>> regenerateRecoveryCodes() {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        com.ayshriv.authplatform.dto.RecoveryCodesResponse response = mfaService.regenerateRecoveryCodes(userId);
        return ResponseEntity.ok(ApiResponse.success("Recovery codes successfully regenerated", response));
    }

}
