package com.gray.anime.ingestion.interfaces.dto;

import java.time.LocalDateTime;

public record ImportTaskView(Long id, String sourceType, String sourceName, String status, int importedWorks, int importedProducts, String errorMessage, LocalDateTime createdAt, LocalDateTime finishedAt) {
}
