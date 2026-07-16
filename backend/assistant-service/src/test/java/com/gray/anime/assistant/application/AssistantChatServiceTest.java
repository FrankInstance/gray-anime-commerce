package com.gray.anime.assistant.application;

import com.gray.anime.assistant.config.AssistantProperties;
import com.gray.anime.assistant.interfaces.dto.AssistantMessageRequest;
import com.gray.anime.common.exception.BizException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssistantChatServiceTest {
    private final AssistantKnowledgeService knowledgeService = mock(AssistantKnowledgeService.class);
    private final CatalogTools catalogTools = mock(CatalogTools.class);
    private final ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);

    @Test
    void ignoresMetadataOnlyStreamChunk() {
        ChatResponse response = mock(ChatResponse.class);

        assertThat(AssistantChatService.responseText(response)).isNull();
    }

    @Test
    void passesThinkingModeOptionToProvider() {
        when(chatClientBuilder.defaultOptions(any(ChatOptions.class))).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(mock(ChatClient.class));
        AssistantProperties properties = new AssistantProperties(
                true, "sk-test-key-value", "qwen-plus", false,
                30, 500, 20, 6000, 800, 30, 4, 0.65);

        new AssistantChatService(
                properties, knowledgeService, catalogTools, chatClientBuilder, new SimpleMeterRegistry());

        ArgumentCaptor<ChatOptions> options = ArgumentCaptor.forClass(ChatOptions.class);
        verify(chatClientBuilder).defaultOptions(options.capture());
        assertThat(options.getValue()).isInstanceOf(OpenAiChatOptions.class);
        assertThat(((OpenAiChatOptions) options.getValue()).getExtraBody())
                .containsEntry("enable_thinking", false);
    }

    @Test
    void rejectsOversizedMessageBeforeCallingKnowledgeOrModel() {
        when(chatClientBuilder.defaultOptions(any(ChatOptions.class))).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(mock(ChatClient.class));
        AssistantProperties properties = new AssistantProperties(
                true, "sk-test-key-value", "qwen-plus", false,
                30, 500, 20, 6000, 800, 30, 4, 0.65);
        AssistantChatService service = new AssistantChatService(
                properties, knowledgeService, catalogTools, chatClientBuilder, new SimpleMeterRegistry());
        AssistantMessageRequest request = new AssistantMessageRequest("问".repeat(501), List.of());

        assertThatThrownBy(() -> service.stream(request, "Bearer token"))
                .isInstanceOf(BizException.class)
                .hasMessage("问题最多 500 字");
        verifyNoInteractions(knowledgeService, catalogTools);
    }
}
