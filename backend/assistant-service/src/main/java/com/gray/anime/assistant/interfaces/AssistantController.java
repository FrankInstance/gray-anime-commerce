package com.gray.anime.assistant.interfaces;

import com.gray.anime.assistant.application.AssistantChatService;
import com.gray.anime.assistant.application.AssistantConversation;
import com.gray.anime.assistant.application.AssistantQuotaService;
import com.gray.anime.assistant.interfaces.dto.AssistantMessageRequest;
import com.gray.anime.assistant.interfaces.dto.AssistantStatusView;
import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
    private final AssistantChatService chatService;
    private final AssistantQuotaService quotaService;

    public AssistantController(AssistantChatService chatService, AssistantQuotaService quotaService) {
        this.chatService = chatService;
        this.quotaService = quotaService;
    }

    @GetMapping("/status")
    ApiResponse<AssistantStatusView> status() {
        return ApiResponse.ok(new AssistantStatusView(chatService.available()));
    }

    @PostMapping(value = "/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter messages(@Valid @RequestBody AssistantMessageRequest body, HttpServletRequest request) {
        CurrentUser user = CurrentUser.from(request);
        quotaService.reserve(user.id());
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        AssistantConversation conversation = chatService.stream(body, authorization);
        SseEmitter emitter = new SseEmitter(35_000L);
        AtomicReference<Disposable> subscription = new AtomicReference<>();

        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("status", "streaming")));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
            return emitter;
        }

        subscription.set(conversation.content().subscribe(
                chunk -> send(emitter, "delta", Map.of("text", chunk), subscription),
                error -> {
                    send(emitter, "error", Map.of("code", "ASSISTANT_UNAVAILABLE",
                            "message", "客服暂时不可用，请稍后再试"), subscription);
                    emitter.complete();
                },
                () -> {
                    send(emitter, "references", Map.of("items", conversation.references().snapshot()), subscription);
                    send(emitter, "done", Map.of("status", "complete"), subscription);
                    emitter.complete();
                }
        ));
        emitter.onCompletion(() -> dispose(subscription));
        emitter.onTimeout(() -> {
            dispose(subscription);
            emitter.complete();
        });
        emitter.onError(error -> dispose(subscription));
        return emitter;
    }

    private void send(SseEmitter emitter, String name, Object data, AtomicReference<Disposable> subscription) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException exception) {
            dispose(subscription);
        }
    }

    private void dispose(AtomicReference<Disposable> subscription) {
        Disposable value = subscription.getAndSet(null);
        if (value != null && !value.isDisposed()) {
            value.dispose();
        }
    }
}
