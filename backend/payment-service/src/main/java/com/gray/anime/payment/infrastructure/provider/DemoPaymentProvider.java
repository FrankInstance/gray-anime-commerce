package com.gray.anime.payment.infrastructure.provider;

import com.gray.anime.payment.application.provider.PaymentCheckout;
import com.gray.anime.payment.application.provider.PaymentProvider;
import com.gray.anime.payment.application.provider.ProviderCheckoutSession;

import java.time.Duration;
import java.time.LocalDateTime;

public final class DemoPaymentProvider implements PaymentProvider {
    public static final String CODE = "DEMO";
    public static final String INTERACTION_MODE = "DEMO_CONFIRMATION";

    private final Duration sessionTtl;

    public DemoPaymentProvider(Duration sessionTtl) {
        if (sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("sessionTtl must be positive");
        }
        this.sessionTtl = sessionTtl;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public ProviderCheckoutSession createSession(PaymentCheckout checkout) {
        return new ProviderCheckoutSession(
                CODE,
                "DEMO-" + checkout.paymentNo(),
                INTERACTION_MODE,
                null,
                LocalDateTime.now().plus(sessionTtl)
        );
    }
}
