package com.gray.anime.assistant.infrastructure.client.dto;

public record WorkCard(
        Long id,
        String title,
        String workType,
        String author,
        String category,
        String description,
        String coverUrl,
        Integer popularity
) {
}
