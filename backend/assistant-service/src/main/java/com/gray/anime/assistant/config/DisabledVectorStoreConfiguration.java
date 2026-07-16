package com.gray.anime.assistant.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "gray.ai.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledVectorStoreConfiguration {
    @Bean
    VectorStore disabledVectorStore() {
        return new VectorStore() {
            @Override
            public void add(List<Document> documents) {
                throw new IllegalStateException("AI is disabled");
            }

            @Override
            public void delete(List<String> idList) {
                throw new IllegalStateException("AI is disabled");
            }

            @Override
            public void delete(Filter.Expression filterExpression) {
                throw new IllegalStateException("AI is disabled");
            }

            @Override
            public List<Document> similaritySearch(SearchRequest request) {
                return List.of();
            }
        };
    }
}
