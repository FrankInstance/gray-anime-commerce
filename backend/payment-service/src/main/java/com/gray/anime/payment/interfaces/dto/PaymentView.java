package com.gray.anime.payment.interfaces.dto;

import java.time.LocalDateTime;

public record PaymentView(String paymentNo, String orderNo, Integer amountCents, String channel, String status, LocalDateTime confirmedAt) {
}
