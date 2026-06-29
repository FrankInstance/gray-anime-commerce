package com.gray.anime.order.interfaces.dto;

public record ReserveStockRequest(Long userId, Long skuId, int quantity, String bizKey) {
}
