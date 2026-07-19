package com.gray.anime.payment.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {
    @Test
    void transitionMatrixOnlyAllowsDocumentedTransitions() {
        assertTransitions(PaymentStatus.CREATED, PaymentStatus.PENDING, PaymentStatus.CANCELLED, PaymentStatus.EXPIRED);
        assertTransitions(PaymentStatus.PENDING, PaymentStatus.CONFIRMED, PaymentStatus.FAILED,
                PaymentStatus.CANCELLED, PaymentStatus.EXPIRED);
        assertTransitions(PaymentStatus.FAILED, PaymentStatus.PENDING, PaymentStatus.CANCELLED, PaymentStatus.EXPIRED);
        assertTransitions(PaymentStatus.CONFIRMED);
        assertTransitions(PaymentStatus.CANCELLED);
        assertTransitions(PaymentStatus.EXPIRED);
    }

    private void assertTransitions(PaymentStatus source, PaymentStatus... allowed) {
        Set<PaymentStatus> expected = Set.of(allowed);
        for (PaymentStatus target : PaymentStatus.values()) {
            assertThat(source.canTransitionTo(target))
                    .as("%s -> %s", source, target)
                    .isEqualTo(expected.contains(target));
        }
    }
}
