package com.gray.anime.assistant.infrastructure.client.dto;

import java.util.List;

public record ProductView(
        Long id,
        String title,
        String productType,
        String description,
        String coverUrl,
        boolean limited,
        String saleStartAt,
        List<SkuView> skus
) {
}
