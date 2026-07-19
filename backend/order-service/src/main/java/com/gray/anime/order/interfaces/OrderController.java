package com.gray.anime.order.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.order.application.OrderApplicationService;
import com.gray.anime.order.interfaces.dto.CreateOrderRequest;
import com.gray.anime.order.interfaces.dto.OrderView;
import com.gray.anime.order.interfaces.dto.PointsOrderRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
    private final OrderApplicationService service;

    public OrderController(OrderApplicationService service) {
        this.service = service;
    }

    @PostMapping("/orders")
    ApiResponse<OrderView> createOrder(@Valid @RequestBody CreateOrderRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(service.createProductOrder(CurrentUser.from(httpRequest), request));
    }

    @GetMapping("/orders")
    ApiResponse<PageResult<OrderView>> myOrders(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.myOrders(CurrentUser.from(request), page, size, status));
    }

    @GetMapping("/orders/{orderNo}")
    ApiResponse<OrderView> myOrder(@PathVariable String orderNo, HttpServletRequest request) {
        return ApiResponse.ok(service.myOrder(CurrentUser.from(request), orderNo));
    }

    @PostMapping("/orders/{orderNo}/cancel")
    ApiResponse<OrderView> cancelOrder(@PathVariable String orderNo, HttpServletRequest request) {
        return ApiResponse.ok(service.cancelOrder(CurrentUser.from(request), orderNo));
    }

    @PostMapping("/vip/orders")
    ApiResponse<OrderView> createVipOrder(HttpServletRequest request) {
        return ApiResponse.ok(service.createVipOrder(CurrentUser.from(request)));
    }

    @PostMapping("/orders/points")
    ApiResponse<OrderView> createPointsOrder(@Valid @RequestBody PointsOrderRequest body, HttpServletRequest request) {
        return ApiResponse.ok(service.createPointsOrder(CurrentUser.from(request), body));
    }

    @PostMapping("/chapters/{id}/purchase")
    ApiResponse<OrderView> purchaseChapter(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(service.purchaseChapter(CurrentUser.from(request), id));
    }
}
