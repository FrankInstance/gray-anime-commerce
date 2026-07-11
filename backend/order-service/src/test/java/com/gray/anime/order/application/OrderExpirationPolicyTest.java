package com.gray.anime.order.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderExpirationPolicyTest {
    private final OrderExpirationPolicy policy = new OrderExpirationPolicy(Duration.ofMinutes(10));
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 11, 12, 0);

    @Test
    void orderIsNotExpiredBeforeTenMinutes() {
        assertFalse(policy.isExpired(now.minusMinutes(9).minusSeconds(59), now));
    }

    @Test
    void orderExpiresAtExactlyTenMinutes() {
        assertTrue(policy.isExpired(now.minusMinutes(10), now));
    }
}
