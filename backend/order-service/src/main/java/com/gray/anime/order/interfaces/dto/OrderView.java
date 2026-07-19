package com.gray.anime.order.interfaces.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OrderView(
        Long id,
        String orderNo,
        String orderType,
        Integer totalCents,
        Integer totalPoints,
        String status,
        String fulfillmentStatus,
        String cancelReason,
        String paymentNo,
        String paymentStatus,
        String paymentChannel,
        LocalDateTime paidAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt,
        List<OrderItemView> items
) {
}
