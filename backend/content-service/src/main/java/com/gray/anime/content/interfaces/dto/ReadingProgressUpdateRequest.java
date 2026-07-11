package com.gray.anime.content.interfaces.dto;

public record ReadingProgressUpdateRequest(Long chapterId, Integer progressPercent) {
}
