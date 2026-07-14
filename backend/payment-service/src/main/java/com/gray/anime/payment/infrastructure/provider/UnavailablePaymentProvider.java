package com.gray.anime.payment.infrastructure.provider;

import com.gray.anime.common.exception.BizException;
import com.gray.anime.payment.application.provider.PaymentCheckout;
import com.gray.anime.payment.application.provider.PaymentProvider;
import com.gray.anime.payment.application.provider.ProviderCheckoutSession;

public final class UnavailablePaymentProvider implements PaymentProvider {
    @Override
    public String code() {
        return "DISABLED";
    }

    @Override
    public ProviderCheckoutSession createSession(PaymentCheckout checkout) {
        throw new BizException("PAYMENT_PROVIDER_UNAVAILABLE", "支付服务暂不可用");
    }
}
