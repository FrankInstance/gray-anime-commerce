package com.gray.anime.content.interfaces.dto;

import java.util.List;

public record WorkDetail(WorkCard work, List<ChapterView> chapters) {
}
