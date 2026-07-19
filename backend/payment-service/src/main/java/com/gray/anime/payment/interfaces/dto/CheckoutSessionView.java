package com.gray.anime.payment.interfaces.dto;

import java.time.LocalDateTime;

public record CheckoutSessionView(
        String provider,
        String sessionId,
        String paymentNo,
        String paymentStatus,
        String interactionMode,
        String redirectUrl,
        LocalDateTime expiresAt
) {
}
