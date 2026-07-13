package com.gray.anime.common.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

public record CurrentUser(Long id, Set<String> roles) {
    public static final String REQUEST_ATTRIBUTE = CurrentUser.class.getName();
    public static final CurrentUser ANONYMOUS = new CurrentUser(0L, Set.of("ANONYMOUS"));

    public static CurrentUser from(HttpServletRequest request) {
        Object currentUser = request.getAttribute(REQUEST_ATTRIBUTE);
        return currentUser instanceof CurrentUser user ? user : ANONYMOUS;
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
