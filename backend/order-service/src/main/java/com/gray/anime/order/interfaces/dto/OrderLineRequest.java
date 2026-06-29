package com.gray.anime.order.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderLineRequest(@NotNull Long skuId, @Min(1) int quantity) {
}
