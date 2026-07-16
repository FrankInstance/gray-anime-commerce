package com.gray.anime.assistant.application;

import com.gray.anime.assistant.interfaces.dto.AssistantReference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AssistantReferenceCollector {
    private final Map<String, AssistantReference> references = new LinkedHashMap<>();

    public synchronized void add(AssistantReference reference) {
        references.putIfAbsent(reference.key(), reference);
    }

    public synchronized void addAll(List<AssistantReference> items) {
        items.forEach(this::add);
    }

    public synchronized List<AssistantReference> snapshot() {
        return List.copyOf(references.values());
    }
}
