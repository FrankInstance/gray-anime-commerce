package com.gray.anime.user.infrastructure.maintenance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Profile("demo")
@ConditionalOnProperty(name = "demo.maintenance.cleanup-enabled", havingValue = "true")
public class DemoMaintenanceConfiguration {
}
