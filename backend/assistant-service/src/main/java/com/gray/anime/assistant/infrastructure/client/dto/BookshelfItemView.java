package com.gray.anime.assistant.infrastructure.client.dto;

public record BookshelfItemView(
        Long workId,
        String title,
        String workType,
        String author,
        String category,
        String description,
        String coverUrl,
        Integer popularity,
        Long lastChapterId,
        Integer lastChapterNo,
        String lastChapterTitle,
        Integer progressPercent,
        String addedAt,
        String updatedAt
) {
}
