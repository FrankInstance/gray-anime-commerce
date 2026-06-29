package com.gray.anime.user.interfaces.dto;

import java.util.Set;

public record TokenResponse(String accessToken, long expiresIn, UserProfile profile, Set<String> roles) {
}
