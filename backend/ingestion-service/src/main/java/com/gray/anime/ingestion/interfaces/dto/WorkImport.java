package com.gray.anime.ingestion.interfaces.dto;

import java.util.List;

public record WorkImport(String title, String workType, String author, String category, String description, String coverUrl, List<ChapterImport> chapters) {
}
