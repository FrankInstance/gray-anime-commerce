package com.gray.anime.assistant.application;

import com.gray.anime.assistant.interfaces.dto.AssistantReference;

import java.util.List;

public record CatalogSearchResult(
        boolean available,
        List<AssistantReference> items,
        String message
) {
    public static CatalogSearchResult found(List<AssistantReference> items) {
        return new CatalogSearchResult(true, List.copyOf(items), items.isEmpty() ? "没有找到匹配内容" : "查询成功");
    }

    public static CatalogSearchResult unavailable() {
        return new CatalogSearchResult(false, List.of(), "目录服务暂时不可用");
    }
}
