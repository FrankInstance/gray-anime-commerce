package com.gray.anime.payment.config;

import com.gray.anime.payment.application.provider.PaymentProvider;
import com.gray.anime.payment.infrastructure.provider.DemoPaymentProvider;
import com.gray.anime.payment.infrastructure.provider.UnavailablePaymentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentProviderConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PaymentProviderConfiguration.class);

    @Test
    void defaultsToAnUnavailableProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PaymentProvider.class);
            assertThat(context.getBean(PaymentProvider.class)).isInstanceOf(UnavailablePaymentProvider.class);
        });
    }

    @Test
    void createsTheDemoProviderOnlyWhenExplicitlyConfigured() {
        contextRunner
                .withPropertyValues(
                        "payment.provider=demo",
                        "payment.checkout-session-ttl-seconds=600"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(PaymentProvider.class);
                    assertThat(context.getBean(PaymentProvider.class)).isInstanceOf(DemoPaymentProvider.class);
                });
    }
}
