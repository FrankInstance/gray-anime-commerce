package com.gray.anime.content.interfaces.dto;

import java.time.LocalDateTime;

public record ReadingProgressView(
        Long workId,
        String title,
        String workType,
        String author,
        String category,
        String description,
        String coverUrl,
        Long chapterId,
        Integer chapterNo,
        String chapterTitle,
        LocalDateTime updatedAt
) {
}
