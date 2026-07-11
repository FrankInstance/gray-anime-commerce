package com.gray.anime.order.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class OrderExpirationPolicy {
    private final Duration paymentTimeout;

    public OrderExpirationPolicy(@Value("${orders.payment-timeout:PT10M}") Duration paymentTimeout) {
        if (paymentTimeout.isZero() || paymentTimeout.isNegative()) {
            throw new IllegalArgumentException("Payment timeout must be positive");
        }
        this.paymentTimeout = paymentTimeout;
    }

    public LocalDateTime cutoff(LocalDateTime now) {
        return now.minus(paymentTimeout);
    }

    public boolean isExpired(LocalDateTime createdAt, LocalDateTime now) {
        return !createdAt.isAfter(cutoff(now));
    }
}
