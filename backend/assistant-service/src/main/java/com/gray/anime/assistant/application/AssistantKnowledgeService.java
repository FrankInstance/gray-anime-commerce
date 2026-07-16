package com.gray.anime.assistant.application;

import com.gray.anime.assistant.config.AssistantProperties;
import com.gray.anime.assistant.interfaces.dto.AssistantReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class AssistantKnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(AssistantKnowledgeService.class);
    private final VectorStore vectorStore;
    private final AssistantProperties properties;

    public AssistantKnowledgeService(VectorStore vectorStore, AssistantProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public KnowledgeContext retrieve(String question, AssistantReferenceCollector collector) {
        if (!properties.available()) {
            return KnowledgeContext.empty();
        }
        try {
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(properties.ragTopK())
                    .similarityThreshold(properties.ragSimilarityThreshold())
                    .build());
            if (documents == null || documents.isEmpty()) {
                return KnowledgeContext.empty();
            }
            List<String> sources = documents.stream()
                    .map(document -> Objects.toString(document.getMetadata().get("source"), ""))
                    .filter(source -> !source.isBlank())
                    .distinct()
                    .toList();
            documents.stream()
                    .map(document -> new AssistantReference(
                            "FAQ",
                            "FAQ:" + document.getMetadata().get("source"),
                            null,
                            Objects.toString(document.getMetadata().get("title"), "帮助中心"),
                            null, null, null, false, List.of(),
                            Objects.toString(document.getMetadata().get("source"), "")))
                    .forEach(collector::add);
            String text = documents.stream()
                    .map(Document::getText)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .reduce((left, right) -> left + "\n\n" + right)
                    .orElse("");
            return new KnowledgeContext(text, sources);
        } catch (RuntimeException exception) {
            log.warn("assistant knowledge retrieval unavailable: {}", exception.getClass().getSimpleName());
            return KnowledgeContext.empty();
        }
    }
}
