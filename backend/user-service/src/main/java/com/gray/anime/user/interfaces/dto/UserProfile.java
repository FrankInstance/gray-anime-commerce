package com.gray.anime.user.interfaces.dto;

import java.time.LocalDateTime;
import java.util.Set;

public record UserProfile(
        Long id,
        String email,
        String username,
        Set<String> roles,
        String status,
        Integer points,
        LocalDateTime vipUntil
) {
}
