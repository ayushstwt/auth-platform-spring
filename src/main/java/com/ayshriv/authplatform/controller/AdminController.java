package com.ayshriv.authplatform.controller;

import com.ayshriv.authplatform.dto.ApiResponse;
import com.ayshriv.authplatform.dto.PageMetadata;
import com.ayshriv.authplatform.dto.UserResponse;
import com.ayshriv.authplatform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Privileged Administration Endpoints")
public class AdminController {

    private final UserService userService;
    private final com.ayshriv.authplatform.config.AppProperties appProperties;

    public AdminController(UserService userService,
                           com.ayshriv.authplatform.config.AppProperties appProperties) {
        this.userService = userService;
        this.appProperties = appProperties;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get paginated list of all users (Admin only)")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        checkAdminMfaEnforcement();
        Page<UserResponse> usersPage = userService.getAllUsers(PageRequest.of(page, size, Sort.by("id").descending()));
        return ResponseEntity.ok(ApiResponse.success(
                "Users retrieved successfully",
                usersPage.getContent(),
                PageMetadata.from(usersPage)
        ));
    }

    private void checkAdminMfaEnforcement() {
        if (!appProperties.mfa().enforceForAdmin()) {
            return;
        }
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            java.util.List<String> amr = jwt.getClaimAsStringList("amr");
            boolean hasMfa = amr != null && amr.stream().anyMatch(f -> f.startsWith("mfa"));
            if (!hasMfa) {
                throw new com.ayshriv.authplatform.exception.ForbiddenException(
                        com.ayshriv.authplatform.constants.ErrorMessages.E034
                );
            }
        }
    }

}
