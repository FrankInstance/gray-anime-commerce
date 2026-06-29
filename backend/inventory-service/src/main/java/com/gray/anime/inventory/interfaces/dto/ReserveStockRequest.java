package com.gray.anime.inventory.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReserveStockRequest(@NotNull Long userId, @NotNull Long skuId, @Min(1) int quantity, @NotBlank String bizKey) {
}
