package com.gray.anime.shop.interfaces.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ProductView(
        Long id,
        String title,
        String productType,
        String description,
        String coverUrl,
        boolean limited,
        LocalDateTime saleStartAt,
        List<SkuView> skus
) {
}
