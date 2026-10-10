package com.iosocket.userservice.dto;

import com.iosocket.userservice.entity.Role;
import com.iosocket.userservice.entity.User;
import com.iosocket.userservice.entity.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        boolean emailVerified,
        UserStatus status,
        Set<String> roles,
        Instant createdOn
) {
    public static UserResponse from(User user) {
        Set<String> roleNames = user.getRoles() != null
                ? user.getRoles().stream().map(Role::getName).collect(Collectors.toSet())
                : Set.of();

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.isEmailVerified(),
                user.getStatus(),
                roleNames,
                user.getCreatedOn()
        );
    }
}
