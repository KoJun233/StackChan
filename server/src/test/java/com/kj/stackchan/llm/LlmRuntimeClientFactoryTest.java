package com.kj.stackchan.llm;

import java.lang.reflect.Modifier;
import java.net.ConnectException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmRuntimeClientFactoryTest {

    @Test
    void preservesOpenAiToolCallingOptionsThroughTheSafeModelWrapper() {
        LlmSettingsService settingsService = mock(LlmSettingsService.class);
        when(settingsService.resolveForInvocation()).thenReturn(new ResolvedLlmSettings(
                "http://localhost:18080/v1", "qwen3.7-plus", "companion prompt", "sk-secret"
        ));
        LlmRuntimeClientFactory factory = new LlmRuntimeClientFactory(settingsService, WebClient.builder());

        ChatModel model = factory.createChatModel();
        assertThat(Modifier.isPublic(model.getClass().getModifiers())).isTrue();
        assertThat(model.getDefaultOptions())
                .isInstanceOf(OpenAiChatOptions.class)
                .satisfies(options -> assertThat(((OpenAiChatOptions) options).getModel())
                        .isEqualTo("qwen3.7-plus"));
    }

    @Test
    void disablesDeepSeekV4ThinkingOnlyForAgentToolInvocations() {
        LlmSettingsService settingsService = mock(LlmSettingsService.class);
        when(settingsService.resolveForInvocation()).thenReturn(new ResolvedLlmSettings(
                "https://api.deepseek.com", "deepseek-v4-flash", "companion prompt", "sk-secret"
        ));
        LlmRuntimeClientFactory factory = new LlmRuntimeClientFactory(settingsService, WebClient.builder());

        OpenAiChatOptions normalOptions = (OpenAiChatOptions) factory.createChatModel().getDefaultOptions();
        OpenAiChatOptions agentOptions = (OpenAiChatOptions) factory.createAgentChatModel().getDefaultOptions();

        assertThat(normalOptions.getExtraBody()).isNullOrEmpty();
        assertThat(agentOptions.getExtraBody()).containsEntry(
                "thinking", Map.of("type", "disabled")
        );
    }

    @Test
    void streamsAnOpenAiCompatibleResponseUsingTheSavedSettings() {
        LlmSettingsService settingsService = mock(LlmSettingsService.class);
        when(settingsService.resolveForInvocation()).thenReturn(new ResolvedLlmSettings(
                "http://localhost:18080/v1", "qwen3.7-plus", "companion prompt", "sk-secret"
        ));
        ExchangeFunction exchangeFunction = request -> {
            assertThat(request.url().toString()).isEqualTo("http://localhost:18080/v1/chat/completions");
            assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer sk-secret");
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .body("""
                            data: {"id":"chat-1","created":1,"model":"qwen3.7-plus","choices":[{"index":0,"delta":{"role":"assistant","content":"pong"},"finish_reason":null}]}

                            data: [DONE]

                            """)
                    .build());
        };

        LlmRuntimeClientFactory factory = new LlmRuntimeClientFactory(
                settingsService,
                WebClient.builder().exchangeFunction(exchangeFunction)
        );

        List<String> content = factory.createChatClient()
                .prompt()
                .user("connection test")
                .stream()
                .content()
                .collectList()
                .block();

        assertThat(content).containsExactly("pong");
    }

    @Test
    void masksProviderTransportErrors() {
        LlmSettingsService settingsService = mock(LlmSettingsService.class);
        when(settingsService.resolveForInvocation()).thenReturn(new ResolvedLlmSettings(
                "http://localhost:18080/v1", "qwen3.7-plus", "companion prompt", "sk-secret"
        ));
        ExchangeFunction exchangeFunction = request -> Mono.error(new WebClientRequestException(
                new ConnectException("connection refused"),
                HttpMethod.POST,
                URI.create("http://localhost:18080/v1/chat/completions"),
                HttpHeaders.EMPTY
        ));
        LlmRuntimeClientFactory factory = new LlmRuntimeClientFactory(
                settingsService,
                WebClient.builder().exchangeFunction(exchangeFunction)
        );

        assertThatThrownBy(() -> factory.createChatClient()
                .prompt()
                .user("connection test")
                .call()
                .content())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("模型服务暂时不可用，请检查 AI 配置")
                .satisfies(error -> {
                    assertThat(error.getMessage()).doesNotContain("sk-secret");
                    assertThat(error.getMessage()).doesNotContain("localhost");
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429})
    void masksProviderClientErrorsWithoutLeakingTheResponseBody(int status) {
        LlmSettingsService settingsService = mock(LlmSettingsService.class);
        when(settingsService.resolveForInvocation()).thenReturn(new ResolvedLlmSettings(
                "http://localhost:18080/v1", "qwen3.7-plus", "companion prompt", "sk-secret"
        ));
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.valueOf(status))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"upstream-secret-detail\"}")
                .build());
        LlmRuntimeClientFactory factory = new LlmRuntimeClientFactory(
                settingsService,
                WebClient.builder().exchangeFunction(exchangeFunction)
        );

        assertThatThrownBy(() -> factory.createChatClient()
                .prompt()
                .user("connection test")
                .call()
                .content())
                .isInstanceOf(LlmProviderUnavailableException.class)
                .hasMessage(LlmProviderUnavailableException.SAFE_MESSAGE)
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("upstream-secret-detail"));
    }

    @Test
    void mapsStoredSecretDecryptionFailuresToInvalidSettings() {
        LlmSettingsService settingsService = mock(LlmSettingsService.class);
        when(settingsService.resolveForInvocation()).thenThrow(new IllegalStateException("Unable to decrypt LLM API key"));
        LlmRuntimeClientFactory factory = new LlmRuntimeClientFactory(settingsService, WebClient.builder());

        assertThatThrownBy(factory::createChatClient)
                .isInstanceOf(InvalidLlmSettingsException.class)
                .hasMessage("AI configuration could not be loaded")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

}
