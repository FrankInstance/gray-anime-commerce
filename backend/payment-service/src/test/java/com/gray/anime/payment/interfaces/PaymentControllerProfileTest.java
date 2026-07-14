package com.gray.anime.payment.interfaces;

import com.gray.anime.payment.application.PaymentApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PaymentControllerProfileTest {
    @Test
    void registersMockConfirmationOnlyForLocalAndTestProfiles() {
        try (AnnotationConfigApplicationContext local = context("local")) {
            assertThat(local.getBeansOfType(PaymentController.class)).hasSize(1);
        }
        try (AnnotationConfigApplicationContext test = context("test")) {
            assertThat(test.getBeansOfType(PaymentController.class)).hasSize(1);
        }
        try (AnnotationConfigApplicationContext production = context("prod")) {
            assertThat(production.getBeansOfType(PaymentController.class)).isEmpty();
        }
    }

    private AnnotationConfigApplicationContext context(String profile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.registerBean(PaymentApplicationService.class, () -> mock(PaymentApplicationService.class));
        context.register(PaymentController.class);
        context.refresh();
        return context;
    }
}
