package com.gray.anime.eventing;

import java.time.LocalDateTime;

public record PaymentConfirmedEvent(String paymentNo, String orderNo, Long userId, Integer amountCents,
                                    String provider, LocalDateTime confirmedAt) {
}
