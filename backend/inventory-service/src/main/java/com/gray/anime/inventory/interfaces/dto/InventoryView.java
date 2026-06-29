package com.gray.anime.inventory.interfaces.dto;

public record InventoryView(Long skuId, int stockAvailable, int stockLocked, int limitPerUser) {
}
