package com.gray.anime.ingestion.interfaces.dto;

import java.util.List;

public record BulkImportRequest(List<WorkImport> works, List<ProductImport> products) {
}
