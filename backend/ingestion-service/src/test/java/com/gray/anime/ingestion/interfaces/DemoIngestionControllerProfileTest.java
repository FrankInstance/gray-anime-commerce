package com.gray.anime.ingestion.interfaces;

import com.gray.anime.ingestion.application.IngestionApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DemoIngestionControllerProfileTest {
    @Test
    void registersDemoImportOnlyForLocalAndTestProfiles() {
        try (AnnotationConfigApplicationContext test = context("test")) {
            assertThat(test.getBeansOfType(DemoIngestionController.class)).hasSize(1);
        }
        try (AnnotationConfigApplicationContext production = context("prod")) {
            assertThat(production.getBeansOfType(DemoIngestionController.class)).isEmpty();
        }
    }

    private AnnotationConfigApplicationContext context(String profile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.registerBean(IngestionApplicationService.class, () -> mock(IngestionApplicationService.class));
        context.register(DemoIngestionController.class);
        context.refresh();
        return context;
    }
}
