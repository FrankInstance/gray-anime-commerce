package com.gray.anime.user.infrastructure.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Profile("demo")
@ConditionalOnProperty(name = "demo.maintenance.cleanup-enabled", havingValue = "true")
class DemoDataCleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoDataCleanupService.class);

    private final DemoDataCleanupRepository repository;
    private final DemoInventoryCacheSynchronizer inventoryCacheSynchronizer;

    DemoDataCleanupService(DemoDataCleanupRepository repository,
                           DemoInventoryCacheSynchronizer inventoryCacheSynchronizer) {
        this.repository = repository;
        this.inventoryCacheSynchronizer = inventoryCacheSynchronizer;
    }

    boolean runIfDue() {
        Optional<LocalDateTime> claimedRun;
        try {
            repository.initializeSchedule();
            claimedRun = repository.claimDueRun();
        } catch (RuntimeException exception) {
            LOGGER.error("Demo cleanup schedule check failed and will be retried", exception);
            return false;
        }
        if (claimedRun.isEmpty()) {
            return false;
        }
        try {
            DemoDataCleanupSummary summary = repository.purgeAndComplete(claimedRun.get());
            refreshInventoryCache();
            LOGGER.info("Demo cleanup completed: users={}, orders={}, payments={}",
                    summary.usersDeleted(), summary.ordersDeleted(), summary.paymentsDeleted());
            return true;
        } catch (RuntimeException exception) {
            markFailed(exception);
            LOGGER.error("Demo cleanup failed and will be retried", exception);
            return false;
        }
    }

    private void markFailed(RuntimeException cleanupFailure) {
        try {
            repository.markFailed(cleanupFailure.getMessage());
        } catch (RuntimeException markerFailure) {
            cleanupFailure.addSuppressed(markerFailure);
        }
    }

    private void refreshInventoryCache() {
        try {
            inventoryCacheSynchronizer.refresh();
        } catch (RuntimeException exception) {
            LOGGER.warn("Demo cleanup committed, but inventory cache refresh failed", exception);
        }
    }
}
