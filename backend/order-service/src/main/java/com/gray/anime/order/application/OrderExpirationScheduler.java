package com.gray.anime.order.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderExpirationScheduler {
    private final OrderLifecycleService lifecycleService;

    public OrderExpirationScheduler(OrderLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(fixedDelayString = "${orders.expiration-scan-delay-ms:30000}")
    public void cancelExpiredPendingOrders() {
        lifecycleService.cancelExpiredPendingOrders();
    }
}
