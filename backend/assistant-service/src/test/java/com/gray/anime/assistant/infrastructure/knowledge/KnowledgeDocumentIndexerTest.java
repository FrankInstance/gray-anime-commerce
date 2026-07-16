package com.gray.anime.assistant.infrastructure.knowledge;

import com.gray.anime.assistant.config.AssistantProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KnowledgeDocumentIndexerTest {
    @Test
    void keepsUtf8ChineseTextAndStableMetadata() {
        KnowledgeDocumentIndexer indexer = new KnowledgeDocumentIndexer(
                mock(ResourcePatternResolver.class), mock(VectorStore.class), mock(StringRedisTemplate.class),
                new AssistantProperties(true, "sk-test-key-value", "qwen-plus", false, 30, 500, 20,
                        6000, 800, 30, 4, 0.65));

        List<Document> documents = indexer.toDocuments("reading.md",
                "# 阅读与书架\n\n阅读页会恢复章节内阅读位置。", "hash");

        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document.getText()).contains("恢复章节内阅读位置");
            assertThat(document.getMetadata()).containsEntry("title", "阅读与书架")
                    .containsEntry("source", "reading.md");
        });
    }
}
