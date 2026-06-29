package com.gray.anime.inventory.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.inventory.application.InventoryApplicationService;
import com.gray.anime.inventory.interfaces.dto.InventoryView;
import com.gray.anime.inventory.interfaces.dto.ReservationView;
import com.gray.anime.inventory.interfaces.dto.ReserveStockRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/inventory")
public class InventoryController {
    private final InventoryApplicationService service;

    public InventoryController(InventoryApplicationService service) {
        this.service = service;
    }

    @PostMapping("/reservations")
    ApiResponse<ReservationView> reserve(@Valid @RequestBody ReserveStockRequest request) {
        return ApiResponse.ok(service.reserve(request));
    }

    @PostMapping("/reservations/{reservationNo}/release")
    ApiResponse<ReservationView> release(@PathVariable String reservationNo) {
        return ApiResponse.ok(service.release(reservationNo));
    }

    @PostMapping("/reservations/{reservationNo}/confirm")
    ApiResponse<ReservationView> confirm(@PathVariable String reservationNo) {
        return ApiResponse.ok(service.confirm(reservationNo));
    }

    @GetMapping("/skus/{skuId}")
    ApiResponse<InventoryView> getBySku(@PathVariable Long skuId) {
        return ApiResponse.ok(service.getBySku(skuId));
    }
}
