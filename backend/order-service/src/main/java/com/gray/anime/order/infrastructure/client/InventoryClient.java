package com.gray.anime.order.infrastructure.client;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.order.interfaces.dto.ReservationView;
import com.gray.anime.order.interfaces.dto.ReserveStockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service")
public interface InventoryClient {
    @PostMapping("/api/v1/internal/inventory/reservations")
    ApiResponse<ReservationView> reserve(@RequestBody ReserveStockRequest request);

    @PostMapping("/api/v1/internal/inventory/reservations/{reservationNo}/confirm")
    ApiResponse<ReservationView> confirm(@PathVariable("reservationNo") String reservationNo);
}
