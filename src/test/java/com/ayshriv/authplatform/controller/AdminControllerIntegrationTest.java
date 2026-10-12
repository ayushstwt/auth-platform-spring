package com.ayshriv.authplatform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ayshriv.authplatform.entity.Role;
import com.ayshriv.authplatform.entity.User;
import com.ayshriv.authplatform.entity.UserStatus;
import com.ayshriv.authplatform.repository.RoleRepository;
import com.ayshriv.authplatform.repository.UserRepository;
import com.ayshriv.authplatform.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        Role roleUser = roleRepository.findByName(Role.ROLE_USER)
                .orElseGet(() -> roleRepository.save(new Role(Role.ROLE_USER)));
        Role roleAdmin = roleRepository.findByName(Role.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(Role.ROLE_ADMIN)));

        User standardUser = new User();
        standardUser.setEmail("user@example.com");
        standardUser.setPasswordHash("hashedPassword123");
        standardUser.setFirstName("Standard");
        standardUser.setLastName("User");
        standardUser.setStatus(UserStatus.ACTIVE);
        standardUser.setRoles(Set.of(roleUser));
        standardUser = userRepository.save(standardUser);
        this.userToken = jwtTokenService.generateAccessToken(standardUser);

        User adminUser = new User();
        adminUser.setEmail("admin@example.com");
        adminUser.setPasswordHash("hashedPassword123");
        adminUser.setFirstName("Admin");
        adminUser.setLastName("User");
        adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setRoles(Set.of(roleUser, roleAdmin));
        adminUser = userRepository.save(adminUser);
        this.adminToken = jwtTokenService.generateAccessToken(adminUser);
    }

    @Test
    @DisplayName("Admin user should be able to list users via /api/v1/admin/users")
    void adminCanAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.metadata.totalItems", greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("Standard user should receive 403 Forbidden on admin endpoint")
    void standardUserDeniedAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.errorCode", is("E006")))
                .andExpect(jsonPath("$.message", is("Access denied")));
    }
}
