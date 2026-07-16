package com.gray.anime.assistant.application;

import reactor.core.publisher.Flux;

public record AssistantConversation(
        Flux<String> content,
        AssistantReferenceCollector references
) {
}
