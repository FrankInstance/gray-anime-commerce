package com.gray.anime.payment.config;

import com.gray.anime.payment.application.provider.PaymentProvider;
import com.gray.anime.payment.infrastructure.provider.DemoPaymentProvider;
import com.gray.anime.payment.infrastructure.provider.UnavailablePaymentProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class PaymentProviderConfiguration {
    @Bean
    @ConditionalOnProperty(name = "payment.provider", havingValue = "demo")
    PaymentProvider demoPaymentProvider(
            @Value("${payment.checkout-session-ttl-seconds:600}") long sessionTtlSeconds
    ) {
        return new DemoPaymentProvider(Duration.ofSeconds(sessionTtlSeconds));
    }

    @Bean
    @ConditionalOnProperty(name = "payment.provider", havingValue = "disabled", matchIfMissing = true)
    PaymentProvider unavailablePaymentProvider() {
        return new UnavailablePaymentProvider();
    }
}
