package com.ayshriv.authplatform.controller;

import com.ayshriv.authplatform.dto.ApiResponse;
import com.ayshriv.authplatform.dto.SessionResponse;
import com.ayshriv.authplatform.security.SecurityUtils;
import com.ayshriv.authplatform.service.SessionManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "User Session and Device Management")
public class SessionController {

    private final SessionManagementService sessionManagementService;

    public SessionController(SessionManagementService sessionManagementService) {
        this.sessionManagementService = sessionManagementService;
    }

    @GetMapping
    @Operation(summary = "List all active sessions and devices for authenticated user")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getActiveSessions(
            @RequestParam(required = false) String currentFamilyId) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        List<SessionResponse> sessions = sessionManagementService.getActiveSessions(userId, currentFamilyId);
        return ResponseEntity.ok(ApiResponse.success("Active sessions retrieved successfully", sessions));
    }

    @DeleteMapping("/{familyId}")
    @Operation(summary = "Revoke a specific session family")
    public ResponseEntity<ApiResponse<Void>> revokeSession(@PathVariable String familyId) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        sessionManagementService.revokeSession(userId, familyId);
        return ResponseEntity.ok(ApiResponse.success("Session revoked successfully", null));
    }

    @DeleteMapping
    @Operation(summary = "Revoke all sessions (optionally keeping current family)")
    public ResponseEntity<ApiResponse<Void>> revokeAllSessions(
            @RequestParam(required = false) String keepFamilyId) {
        Long userId = SecurityUtils.getAuthenticatedUserId();
        if (keepFamilyId != null && !keepFamilyId.isBlank()) {
            sessionManagementService.revokeOtherSessions(userId, keepFamilyId);
            return ResponseEntity.ok(ApiResponse.success("Other sessions revoked successfully", null));
        } else {
            sessionManagementService.revokeOtherSessions(userId, null);
            return ResponseEntity.ok(ApiResponse.success("All sessions revoked successfully", null));
        }
    }
}
