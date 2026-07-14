package com.gray.anime.payment.application.provider;

public interface PaymentProvider {
    String code();

    ProviderCheckoutSession createSession(PaymentCheckout checkout);
}
