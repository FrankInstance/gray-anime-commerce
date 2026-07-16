package com.gray.anime.assistant.infrastructure.client.dto;

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
        Integer progressPercent,
        String updatedAt
) {
}
