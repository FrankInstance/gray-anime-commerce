package com.gray.anime.order.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.order.application.OrderApplicationService;
import com.gray.anime.order.interfaces.dto.DailyMetrics;
import com.gray.anime.order.interfaces.dto.OrderView;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOrderController {
    private final OrderApplicationService service;

    public AdminOrderController(OrderApplicationService service) {
        this.service = service;
    }

    @GetMapping("/orders")
    ApiResponse<PageResult<OrderView>> orders(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(service.adminOrders(page, size, status));
    }

    @GetMapping("/dashboard/daily-metrics")
    ApiResponse<DailyMetrics> dailyMetrics() {
        return ApiResponse.ok(service.dailyMetrics());
    }
}
