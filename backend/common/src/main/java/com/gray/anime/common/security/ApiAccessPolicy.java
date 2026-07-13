package com.gray.anime.common.security;

public final class ApiAccessPolicy {
    private ApiAccessPolicy() {
    }

    public static boolean requiresLogin(String path) {
        return requiresAdmin(path)
                || path.startsWith("/api/v1/users/")
                || path.startsWith("/api/v1/checkins")
                || path.startsWith("/api/v1/cart")
                || path.startsWith("/api/v1/orders")
                || path.startsWith("/api/v1/reading")
                || path.startsWith("/api/v1/vip")
                || path.contains("/bookshelf")
                || path.contains("/purchase")
                || path.startsWith("/api/v1/payments");
    }

    public static boolean requiresAdmin(String path) {
        return path.startsWith("/api/v1/admin/");
    }

    public static boolean isInternal(String path) {
        return path.startsWith("/api/v1/internal/");
    }
}
