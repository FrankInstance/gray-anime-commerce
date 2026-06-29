package com.gray.anime.common.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record CurrentUser(Long id, Set<String> roles) {
    public static final CurrentUser ANONYMOUS = new CurrentUser(0L, Set.of("ANONYMOUS"));

    public static CurrentUser from(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            return ANONYMOUS;
        }
        String rolesHeader = request.getHeader("X-User-Roles");
        Set<String> roles = rolesHeader == null || rolesHeader.isBlank()
                ? Set.of("USER")
                : Arrays.stream(rolesHeader.split(",")).map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        return new CurrentUser(Long.parseLong(userId), roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isVip() {
        return roles.contains("VIP") || roles.contains("ADMIN") || roles.contains("SUPER_ADMIN");
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN") || roles.contains("SUPER_ADMIN");
    }
}
