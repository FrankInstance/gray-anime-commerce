package com.gray.anime.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gray.ai")
public record AssistantProperties(
        boolean enabled,
        String apiKey,
        String chatModel,
        boolean enableThinking,
        int dailyLimit,
        int maxMessageLength,
        int maxHistoryMessages,
        int maxHistoryCharacters,
        int maxOutputTokens,
        int timeoutSeconds,
        int ragTopK,
        double ragSimilarityThreshold
) {
    public boolean available() {
        return enabled && apiKey != null && !apiKey.isBlank() && !"disabled".equals(apiKey);
    }
}
