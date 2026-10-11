package com.ayshriv.authplatform.controller;

import com.ayshriv.authplatform.dto.ApiResponse;
import com.ayshriv.authplatform.dto.AuthResponse;
import com.ayshriv.authplatform.dto.MfaChallengeSendRequest;
import com.ayshriv.authplatform.dto.MfaVerifyRequest;
import com.ayshriv.authplatform.service.MfaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/mfa")
@Tag(name = "MFA Authentication", description = "Multi-Factor Authentication Challenge and Verification")
public class MfaController {

    private final MfaService mfaService;

    public MfaController(MfaService mfaService) {
        this.mfaService = mfaService;
    }

    @PostMapping("/challenge/send")
    @Operation(summary = "Request fallback MFA code via verified Email or SMS")
    public ResponseEntity<ApiResponse<Void>> sendMfaChallenge(
            @Valid @RequestBody MfaChallengeSendRequest request,
            HttpServletRequest servletRequest) {
        mfaService.sendMfaOtp(request, servletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("MFA verification code sent successfully", null));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify MFA code (TOTP, Email OTP, SMS OTP, or Recovery Code) and obtain tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyMfa(
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletRequest servletRequest) {
        AuthResponse response = mfaService.verifyMfa(
                request,
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader("User-Agent")
        );
        return ResponseEntity.ok(ApiResponse.success("MFA authentication successful", response));
    }
}
