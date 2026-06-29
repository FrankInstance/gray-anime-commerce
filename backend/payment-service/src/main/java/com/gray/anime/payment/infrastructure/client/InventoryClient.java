package com.gray.anime.payment.infrastructure.client;

import com.gray.anime.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "inventory-service")
public interface InventoryClient {
    @PostMapping("/api/v1/internal/inventory/reservations/{reservationNo}/confirm")
    ApiResponse<Object> confirm(@PathVariable("reservationNo") String reservationNo);
}
