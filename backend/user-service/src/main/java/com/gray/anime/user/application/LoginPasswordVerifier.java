package com.gray.anime.user.application;

public interface LoginPasswordVerifier {
    boolean matches(String rawPassword, String storedPasswordHash);
}
