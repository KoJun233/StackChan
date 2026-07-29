package com.kj.stackchan.llm;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

@Component
public class LlmRuntimeClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(LlmRuntimeClientFactory.class);

    private final LlmSettingsService settingsService;
    private final WebClient.Builder webClientBuilder;

    public LlmRuntimeClientFactory(LlmSettingsService settingsService, WebClient.Builder webClientBuilder) {
        this.settingsService = settingsService;
        this.webClientBuilder = webClientBuilder;
    }

    public ChatClient createChatClient() {
        return ChatClient.create(createChatModel());
    }

    public ChatModel createChatModel() {
        return createChatModel(false);
    }

    public ChatModel createAgentChatModel() {
        return createChatModel(true);
    }

    private ChatModel createChatModel(boolean agentInvocation) {
        try {
            ResolvedLlmSettings settings = settingsService.resolveForInvocation();
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(settings.baseUrl())
                    .apiKey(settings.apiKey())
                    .completionsPath("/chat/completions")
                    .webClientBuilder(webClientBuilder)
                    .build();
            OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(settings.model());
            if (agentInvocation && requiresNonThinkingAgentMode(settings)) {
                options.extraBody(Map.of("thinking", Map.of("type", "disabled")));
            }
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(options.build())
                    .retryTemplate(RetryUtils.SHORT_RETRY_TEMPLATE)
                    .build();
            return new SafeChatModel(chatModel);
        } catch (InvalidLlmSettingsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidLlmSettingsException("AI configuration could not be loaded", exception);
        }
    }

    public static final class SafeChatModel implements ChatModel {

        private final ChatModel delegate;

        private SafeChatModel(ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            try {
                return delegate.call(prompt);
            } catch (RuntimeException exception) {
                throw mapProviderException(exception);
            }
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return delegate.stream(prompt).onErrorMap(LlmRuntimeClientFactory::mapProviderException);
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return delegate.getDefaultOptions();
        }
    }

    private boolean requiresNonThinkingAgentMode(ResolvedLlmSettings settings) {
        try {
            String host = URI.create(settings.baseUrl()).getHost();
            return host != null
                    && host.equalsIgnoreCase("api.deepseek.com")
                    && settings.model().toLowerCase(Locale.ROOT).startsWith("deepseek-v4-");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static RuntimeException mapProviderException(Throwable exception) {
        WebClientResponseException responseException = findResponseException(exception);
        if (responseException != null) {
            logger.warn("LLM provider request failed with HTTP status {}", responseException.getStatusCode().value());
        }
        if (isProviderUnavailable(exception)) {
            return new LlmProviderUnavailableException();
        }
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(exception);
    }

    private static WebClientResponseException findResponseException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof WebClientResponseException responseException) {
                return responseException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean isProviderUnavailable(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof WebClientRequestException || current instanceof ResourceAccessException) {
                return true;
            }
            if (current instanceof WebClientResponseException responseException
                    && isServerError(responseException.getStatusCode())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isServerError(HttpStatusCode statusCode) {
        return statusCode.is5xxServerError();
    }
}
