package com.ayshriv.authplatform.controller;

import com.ayshriv.authplatform.dto.UserResponse;
import com.ayshriv.authplatform.entity.Role;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.entity.UserStatus;
import com.ayshriv.authplatform.repository.RoleRepository;
import com.ayshriv.authplatform.repository.UserRepository;
import com.ayshriv.authplatform.security.JwtTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User adminUser;
    private User targetUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        Role roleUser = roleRepository.findByName(Role.ROLE_USER)
                .orElseGet(() -> roleRepository.save(new Role(Role.ROLE_USER)));
        Role roleAdmin = roleRepository.findByName(Role.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(Role.ROLE_ADMIN)));

        adminUser = new User();
        adminUser.setEmail("admin.hardened@example.com");
        adminUser.setPasswordHash("hashedPassword123");
        adminUser.setFirstName("Admin");
        adminUser.setLastName("Security");
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setRoles(Set.of(roleUser, roleAdmin));
        adminUser = userRepository.save(adminUser);
        adminToken = jwtTokenService.generateAccessToken(adminUser);

        targetUser = new User();
        targetUser.setEmail("target.subject@example.com");
        targetUser.setPasswordHash("hashedPassword123");
        targetUser.setFirstName("Target");
        targetUser.setLastName("User");
        targetUser.setStatus(UserStatus.ACTIVE);
        targetUser.setRoles(Set.of(roleUser));
        targetUser = userRepository.save(targetUser);
    }

    @Test
    @DisplayName("Admin can lock and unlock a user")
    void adminLockAndUnlockUser() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + targetUser.getId() + "/lock")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("minutes", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.status", is("LOCKED")));

        mockMvc.perform(post("/api/v1/admin/users/" + targetUser.getId() + "/unlock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @DisplayName("Admin can disable user and force logout")
    void adminDisableAndForceLogout() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + targetUser.getId() + "/disable")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data.status", is("DISABLED")));

        mockMvc.perform(post("/api/v1/admin/users/" + targetUser.getId() + "/force-logout")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")));
    }

    @Test
    @DisplayName("Admin can query audit logs with pagination")
    void adminQueryAuditLogs() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.metadata.currentPage", is(0)));
    }
}
