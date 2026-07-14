package com.gray.anime.payment.interfaces.dto;

import java.time.LocalDateTime;

public record CheckoutSessionView(
        String provider,
        String sessionId,
        String paymentNo,
        String interactionMode,
        String redirectUrl,
        LocalDateTime expiresAt
) {
}
