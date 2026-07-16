package com.gray.anime.assistant.infrastructure.client.dto;

public record SkuView(
        Long id,
        String skuName,
        int priceCents,
        int vipPriceCents
) {
}
