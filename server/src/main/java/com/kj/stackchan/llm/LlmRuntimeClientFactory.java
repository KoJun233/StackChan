package com.kj.stackchan.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
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

    private final LlmSettingsService settingsService;
    private final WebClient.Builder webClientBuilder;

    public LlmRuntimeClientFactory(LlmSettingsService settingsService, WebClient.Builder webClientBuilder) {
        this.settingsService = settingsService;
        this.webClientBuilder = webClientBuilder;
    }

    public ChatClient createChatClient() {
        try {
            ResolvedLlmSettings settings = settingsService.resolveForInvocation();
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(settings.baseUrl())
                    .apiKey(settings.apiKey())
                    .completionsPath("/chat/completions")
                    .webClientBuilder(webClientBuilder)
                    .build();
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder().model(settings.model()).build())
                    .retryTemplate(RetryUtils.SHORT_RETRY_TEMPLATE)
                    .build();
            return ChatClient.create(new SafeChatModel(chatModel));
        } catch (InvalidLlmSettingsException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidLlmSettingsException("AI configuration could not be loaded", exception);
        }
    }

    private static final class SafeChatModel implements ChatModel {

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
    }

    private static RuntimeException mapProviderException(Throwable exception) {
        if (isProviderUnavailable(exception)) {
            return new LlmProviderUnavailableException();
        }
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(exception);
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
