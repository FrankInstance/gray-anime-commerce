package com.gray.anime.assistant.application;

import java.util.List;

public record KnowledgeContext(String text, List<String> sources) {
    public static KnowledgeContext empty() {
        return new KnowledgeContext("", List.of());
    }
}
