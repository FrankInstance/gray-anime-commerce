package com.gray.anime.order.interfaces.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(@NotEmpty List<@Valid OrderLineRequest> items) {
}
