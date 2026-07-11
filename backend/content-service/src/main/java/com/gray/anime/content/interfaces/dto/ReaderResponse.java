package com.gray.anime.content.interfaces.dto;

import java.util.List;

public record ReaderResponse(Long chapterId, String title, boolean unlocked, String text, List<String> images, Integer progressPercent) {
}
