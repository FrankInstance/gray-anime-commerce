package com.gray.anime.user.infrastructure.maintenance;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoMaintenanceProfileTest {
    @Test
    void schedulingIsRegisteredOnlyForAnExplicitlyEnabledDemoProfile() {
        try (AnnotationConfigApplicationContext demo = context("demo", true)) {
            assertThat(demo.getBeansOfType(DemoMaintenanceConfiguration.class)).hasSize(1);
        }
        try (AnnotationConfigApplicationContext disabledDemo = context("demo", false)) {
            assertThat(disabledDemo.getBeansOfType(DemoMaintenanceConfiguration.class)).isEmpty();
        }
        try (AnnotationConfigApplicationContext production = context("prod", true)) {
            assertThat(production.getBeansOfType(DemoMaintenanceConfiguration.class)).isEmpty();
        }
    }

    private AnnotationConfigApplicationContext context(String profile, boolean enabled) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "test",
                Map.of("demo.maintenance.cleanup-enabled", Boolean.toString(enabled))
        ));
        context.register(DemoMaintenanceConfiguration.class);
        context.refresh();
        return context;
    }
}
