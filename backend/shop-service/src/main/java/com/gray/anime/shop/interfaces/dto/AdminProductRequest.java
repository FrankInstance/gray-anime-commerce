package com.gray.anime.shop.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record AdminProductRequest(
        @NotBlank String title,
        String productType,
        String description,
        String coverUrl,
        boolean limited,
        LocalDateTime saleStartAt,
        @NotBlank String skuName,
        @Min(1) int priceCents
) {
}
