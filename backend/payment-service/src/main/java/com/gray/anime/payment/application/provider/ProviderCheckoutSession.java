package com.gray.anime.payment.application.provider;

import java.time.LocalDateTime;

public record ProviderCheckoutSession(
        String provider,
        String sessionId,
        String interactionMode,
        String redirectUrl,
        LocalDateTime expiresAt
) {
}
