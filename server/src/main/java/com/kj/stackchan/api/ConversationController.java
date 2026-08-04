package com.kj.stackchan.api;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import com.kj.stackchan.agent.AgentChannel;
import com.kj.stackchan.agent.AgentInvocationContext;
import com.kj.stackchan.agent.AgentOrchestrator;
import com.kj.stackchan.conversation.ConversationMessageSnapshot;
import com.kj.stackchan.conversation.ConversationSnapshot;
import com.kj.stackchan.conversation.ConversationService;
import com.kj.stackchan.conversation.GenerationStart;
import com.kj.stackchan.conversation.MessageRole;
import com.kj.stackchan.llm.LlmSettingsService;
import com.kj.stackchan.llm.LlmProviderUnavailableException;
import com.kj.stackchan.memory.CompanionPromptService;
import com.kj.stackchan.memory.CompletedTurnMemoryCoordinator;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final AgentOrchestrator agentOrchestrator;
    private final LlmSettingsService llmSettingsService;
    private final CompanionPromptService companionPromptService;

    @Autowired(required = false)
    private CompletedTurnMemoryCoordinator completedTurnMemoryCoordinator;

    public ConversationController(
            ConversationService conversationService,
            AgentOrchestrator agentOrchestrator,
            LlmSettingsService llmSettingsService,
            CompanionPromptService companionPromptService
    ) {
        this.conversationService = conversationService;
        this.agentOrchestrator = agentOrchestrator;
        this.llmSettingsService = llmSettingsService;
        this.companionPromptService = companionPromptService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationSnapshot createConversation() {
        return conversationService.createConversation();
    }

    @GetMapping
    public List<ConversationSnapshot> listConversations() {
        return conversationService.listConversations();
    }

    @GetMapping("/{conversationId}/messages")
    public List<ConversationMessageSnapshot> getMessages(@PathVariable UUID conversationId) {
        return conversationService.getMessages(conversationId);
    }

    @PostMapping(
            path = "/{conversationId}/messages:stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8"
    )
    public Flux<ServerSentEvent<Object>> streamMessage(
            @PathVariable UUID conversationId,
            @Valid @RequestBody StreamMessageRequest request,
            HttpServletResponse response
    ) {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        List<ConversationMessageSnapshot> history = conversationService.loadHistory(conversationId);
        GenerationStart start = conversationService.startGeneration(
                conversationId,
                request.clientMessageId(),
                request.content()
        );
        if (start.duplicate()) {
            return replayDuplicate(start).subscribeOn(Schedulers.boundedElastic());
        }
        GenerationContentBuffer generatedContent = new GenerationContentBuffer();
        return Flux.defer(() -> {
            List<Message> modelHistory = history.stream().map(this::toModelMessage).toList();
            CompanionPromptService.PromptAssembly promptAssembly = companionPromptService.assembleWithMemoryContext(
                    conversationId,
                    llmSettingsService.resolveForInvocation().systemPrompt(),
                    "",
                    request.content()
            );
            if (promptAssembly == null) {
                promptAssembly = new CompanionPromptService.PromptAssembly(
                        companionPromptService.assemble(
                                conversationId,
                                llmSettingsService.resolveForInvocation().systemPrompt()
                        ),
                        List.of()
                );
            }
            String systemPrompt = promptAssembly.prompt();
            List<UUID> usedMemoryIds = promptAssembly.memoryIds();
            Flux<ServerSentEvent<Object>> deltas = agentOrchestrator.stream(new AgentOrchestrator.AgentRequest(
                            new AgentInvocationContext(
                                    start.assistantMessageId(),
                                    conversationId,
                                    null,
                                    AgentChannel.WEB
                            ),
                            systemPrompt,
                            modelHistory,
                            request.content()
                    ))
                    .map(text -> {
                        generatedContent.append(text);
                        return event("delta", new DeltaEvent(start.assistantMessageId(), text));
                    });
            Mono<ServerSentEvent<Object>> completed = Mono.fromSupplier(() -> {
                String content = generatedContent.snapshot();
                conversationService.completeGeneration(start.assistantMessageId(), content);
                if (completedTurnMemoryCoordinator != null) {
                    completedTurnMemoryCoordinator.complete(
                            start.assistantMessageId(),
                            start.assistantMessageId(),
                            null,
                            request.content(),
                            content,
                            usedMemoryIds,
                            true
                    );
                }
                return event("completed", new CompletedEvent(start.assistantMessageId(), content));
            });
            return Flux.concat(
                    Mono.just(event("message", new MessageStartedEvent(
                            start.conversationId(),
                            start.userMessageId(),
                            start.assistantMessageId()
                    ))),
                    deltas,
                    completed
            );
        })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    String errorCode = error instanceof LlmProviderUnavailableException
                            ? "provider_unavailable"
                            : "generation_failed";
                    String errorMessage = LlmProviderUnavailableException.SAFE_MESSAGE;
                    conversationService.failGeneration(
                            start.assistantMessageId(),
                            errorCode,
                            generatedContent.snapshot()
                    );
                    return Mono.just(event("error", new ErrorEvent(
                            start.assistantMessageId(),
                            errorCode,
                            errorMessage
                    )));
                })
                .doOnCancel(() -> conversationService.interruptGeneration(
                        start.assistantMessageId(),
                        generatedContent.snapshot()
                ));
    }

    private Message toModelMessage(ConversationMessageSnapshot message) {
        if (message.role() == MessageRole.USER) {
            return new UserMessage(message.content());
        }
        return new AssistantMessage(message.content());
    }

    private Flux<ServerSentEvent<Object>> replayDuplicate(GenerationStart start) {
        ServerSentEvent<Object> message = event("message", new MessageStartedEvent(
                start.conversationId(),
                start.userMessageId(),
                start.assistantMessageId()
        ));
        return switch (start.assistantStatus()) {
            case COMPLETED -> Flux.just(message, event("completed", new CompletedEvent(
                    start.assistantMessageId(),
                    start.assistantContent()
            )));
            case FAILED -> Flux.just(message, event("error", new ErrorEvent(
                    start.assistantMessageId(),
                    "generation_failed",
                    LlmProviderUnavailableException.SAFE_MESSAGE
            )));
            case INTERRUPTED -> Flux.just(message, event("interrupted", new InterruptedEvent(
                    start.assistantMessageId(),
                    start.assistantContent()
            )));
            case STREAMING -> Flux.just(message);
        };
    }

    private ServerSentEvent<Object> event(String event, Object data) {
        return ServerSentEvent.builder(data).event(event).build();
    }

    public record StreamMessageRequest(
            @NotNull UUID clientMessageId,
            @NotBlank @Size(max = 12000) String content
    ) {
    }

    public record MessageStartedEvent(UUID conversationId, UUID userMessageId, UUID assistantMessageId) {
    }

    public record DeltaEvent(UUID messageId, String text) {
    }

    public record CompletedEvent(UUID messageId, String content) {
    }

    public record ErrorEvent(UUID messageId, String code, String message) {
    }

    public record InterruptedEvent(UUID messageId, String content) {
    }
}
