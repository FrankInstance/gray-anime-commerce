package com.gray.anime.payment.domain;

import java.util.Set;

public enum PaymentStatus {
    CREATED,
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED,
    EXPIRED;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case CREATED -> Set.of(PENDING, CANCELLED, EXPIRED).contains(next);
            case PENDING -> Set.of(CONFIRMED, FAILED, CANCELLED, EXPIRED).contains(next);
            case FAILED -> Set.of(PENDING, CANCELLED, EXPIRED).contains(next);
            case CONFIRMED, CANCELLED, EXPIRED -> false;
        };
    }

    public boolean isTerminal() {
        return this == CONFIRMED || this == CANCELLED || this == EXPIRED;
    }

    public static PaymentStatus from(String value) {
        return PaymentStatus.valueOf(value);
    }
}
