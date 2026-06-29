package com.gray.anime.common.security;

import java.time.Instant;
import java.util.Set;

public record JwtClaims(Long userId, Set<String> roles, Instant expiresAt) {
}
