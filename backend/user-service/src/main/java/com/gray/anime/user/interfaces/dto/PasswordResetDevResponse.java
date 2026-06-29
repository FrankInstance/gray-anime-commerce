package com.gray.anime.user.interfaces.dto;

import java.time.LocalDateTime;

public record PasswordResetDevResponse(String channel, String devToken, LocalDateTime expiresAt) {
}
