package com.gray.anime.content.interfaces.dto;

import java.time.LocalDateTime;

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
        LocalDateTime addedAt,
        LocalDateTime updatedAt
) {
}
