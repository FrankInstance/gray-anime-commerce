package com.gray.anime.user.application;

import com.gray.anime.user.interfaces.dto.TokenResponse;

public record AuthSessionResult(TokenResponse tokenResponse, String refreshToken) {
}
