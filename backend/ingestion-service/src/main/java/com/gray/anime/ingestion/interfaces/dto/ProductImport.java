package com.gray.anime.ingestion.interfaces.dto;

import java.time.LocalDateTime;

public record ProductImport(String title, String productType, String description, String coverUrl, boolean limited, LocalDateTime saleStartAt, String skuName, int priceCents, int stock, int limitPerUser) {
}
