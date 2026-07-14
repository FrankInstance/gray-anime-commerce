package com.gray.anime.payment.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckoutSessionRequest(@NotBlank String paymentNo) {
}
