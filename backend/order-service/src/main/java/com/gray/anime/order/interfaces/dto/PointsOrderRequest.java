package com.gray.anime.order.interfaces.dto;

import jakarta.validation.constraints.NotNull;

public record PointsOrderRequest(@NotNull Integer amountCents) {
}
