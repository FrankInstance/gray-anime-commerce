package com.gray.anime.order.domain;

import java.util.Set;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        if (this == next) {
            return true;
        }
        return switch (this) {
            case PENDING_PAYMENT -> Set.of(PAID, CANCELLED).contains(next);
            case PAID, CANCELLED -> false;
        };
    }

    public static OrderStatus from(String value) {
        return OrderStatus.valueOf(value);
    }
}
