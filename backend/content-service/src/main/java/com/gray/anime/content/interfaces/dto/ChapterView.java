package com.gray.anime.content.interfaces.dto;

public record ChapterView(Long id, Integer chapterNo, String title, boolean free, Integer pricePoints, boolean unlocked, String accessLabel) {
}
