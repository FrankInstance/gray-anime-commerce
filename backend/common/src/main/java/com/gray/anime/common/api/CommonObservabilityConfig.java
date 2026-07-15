package com.gray.anime.common.api;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnClass({MeterRegistry.class, MeterRegistryCustomizer.class})
public class CommonObservabilityConfig {
    @Bean
    MeterRegistryCustomizer<MeterRegistry> applicationNameMetricTag(Environment environment) {
        String applicationName = environment.getProperty("spring.application.name", "unknown-service");
        return registry -> registry.config().commonTags("application", applicationName);
    }
}
