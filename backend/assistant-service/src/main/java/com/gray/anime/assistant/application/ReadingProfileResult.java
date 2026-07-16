package com.gray.anime.assistant.application;

import java.util.List;

public record ReadingProfileResult(
        boolean available,
        List<ReadingProfileItem> bookshelf,
        List<ReadingProfileItem> recentReading,
        String message
) {
    public static ReadingProfileResult unavailable() {
        return new ReadingProfileResult(false, List.of(), List.of(), "阅读记录暂时不可用");
    }

    public record ReadingProfileItem(
            Long workId,
            String title,
            String workType,
            String author,
            String category,
            Integer chapterNo,
            Integer progressPercent
    ) {
    }
}
