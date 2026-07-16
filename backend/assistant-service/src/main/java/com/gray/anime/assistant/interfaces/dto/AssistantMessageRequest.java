package com.gray.anime.assistant.interfaces.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AssistantMessageRequest(
        @NotBlank @Size(max = 500) String message,
        @Size(max = 20) List<@Valid AssistantHistoryMessage> history
) {
    public AssistantMessageRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
