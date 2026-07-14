package com.gray.anime.user.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PasswordResetDevResponse(String channel, String devToken, LocalDateTime expiresAt) {
}
