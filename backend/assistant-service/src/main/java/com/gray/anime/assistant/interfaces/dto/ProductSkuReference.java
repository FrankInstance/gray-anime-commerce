package com.gray.anime.assistant.interfaces.dto;

public record ProductSkuReference(
        Long id,
        String skuName,
        int priceCents,
        int vipPriceCents
) {
}
