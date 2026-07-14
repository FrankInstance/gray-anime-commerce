package com.gray.anime.user.infrastructure.maintenance;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("demo")
@ConditionalOnProperty(name = "demo.maintenance.cleanup-enabled", havingValue = "true")
class DemoDataCleanupScheduler {
    private final DemoDataCleanupService cleanupService;

    DemoDataCleanupScheduler(DemoDataCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @EventListener(ApplicationReadyEvent.class)
    void checkAfterStartup() {
        cleanupService.runIfDue();
    }

    @Scheduled(cron = "${demo.maintenance.cleanup-check-cron:0 0 4 * * *}", zone = "Asia/Shanghai")
    void checkDaily() {
        cleanupService.runIfDue();
    }
}
