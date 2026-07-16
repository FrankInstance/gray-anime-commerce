package com.gray.anime.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gray.ai.qdrant")
public record QdrantProperties(
        String host,
        int port,
        boolean useTls,
        String collectionName
) {
}
