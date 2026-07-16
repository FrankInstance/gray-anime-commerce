package com.gray.anime.assistant.application;

import com.gray.anime.assistant.config.AssistantProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantKnowledgeServiceTest {
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final AssistantProperties properties = new AssistantProperties(
            true, "sk-test-key-value", "qwen-plus", false,
            30, 500, 20, 6000, 800, 30, 4, 0.65);

    @Test
    void lowConfidenceSearchDoesNotInventKnowledgeReferences() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        AssistantReferenceCollector collector = new AssistantReferenceCollector();

        KnowledgeContext result = new AssistantKnowledgeService(vectorStore, properties)
                .retrieve("完全无关的问题", collector);

        assertThat(result.text()).isEmpty();
        assertThat(collector.snapshot()).isEmpty();
    }

    @Test
    void retrievedDocumentProducesTrustedContextAndSource() {
        Document document = Document.builder().id("doc-1").text("待支付订单十分钟后取消")
                .metadata("source", "cart-and-orders.md")
                .metadata("title", "购物车与订单")
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(document));
        AssistantReferenceCollector collector = new AssistantReferenceCollector();

        KnowledgeContext result = new AssistantKnowledgeService(vectorStore, properties)
                .retrieve("订单多久取消", collector);

        assertThat(result.text()).contains("十分钟");
        assertThat(result.sources()).containsExactly("cart-and-orders.md");
        assertThat(collector.snapshot()).singleElement()
                .satisfies(reference -> assertThat(reference.kind()).isEqualTo("FAQ"));
    }
}
