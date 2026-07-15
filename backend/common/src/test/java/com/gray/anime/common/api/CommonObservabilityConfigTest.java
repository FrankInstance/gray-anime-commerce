package com.gray.anime.common.api;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class CommonObservabilityConfigTest {
    @Test
    void applicationNameIsAttachedToEveryMetric() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "test-service");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new CommonObservabilityConfig().applicationNameMetricTag(environment).customize(registry);
        registry.counter("gray.test.counter").increment();

        assertThat(registry.get("gray.test.counter").counter().getId().getTag("application"))
                .isEqualTo("test-service");
    }
}
