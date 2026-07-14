package com.gray.anime.payment.application.provider;

public record PaymentCheckout(String paymentNo, String orderNo, Integer amountCents) {
}
