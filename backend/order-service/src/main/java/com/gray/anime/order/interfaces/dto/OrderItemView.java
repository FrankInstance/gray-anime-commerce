package com.gray.anime.order.interfaces.dto;

public record OrderItemView(String itemType, Long refId, Long skuId, String title, int quantity, Integer unitPriceCents, Integer unitPoints, String reservationNo) {
}
