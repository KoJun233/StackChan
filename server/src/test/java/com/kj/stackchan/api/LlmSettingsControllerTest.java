package com.kj.stackchan.api;

import java.time.Instant;

import com.kj.stackchan.llm.InvalidLlmSettingsException;
import com.kj.stackchan.llm.LlmSettingsService;
import com.kj.stackchan.llm.LlmProviderUnavailableException;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LlmSettingsController.class)
@Import(SecurityConfiguration.class)
class LlmSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LlmSettingsService llmSettingsService;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @MockitoBean
    private LlmRuntimeClientFactory llmRuntimeClientFactory;

    @Test
    void returnsConfigurationWithoutAnyApiKeyField() throws Exception {
        when(llmSettingsService.getSettings()).thenReturn(new LlmSettingsService.LlmSettingsSnapshot(
                "https://api.example.com/v1",
                "companion-model",
                "你是一个温柔的陪伴机器人。",
                true,
                Instant.parse("2026-07-17T12:00:00Z")
        ));

        mockMvc.perform(get("/api/v1/settings/llm").with(user("admin").roles("ADMIN")).accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"baseUrl":"https://api.example.com/v1","model":"companion-model","systemPrompt":"你是一个温柔的陪伴机器人。","apiKeyConfigured":true,"updatedAt":"2026-07-17T12:00:00Z"}
                        """, STRICT));
    }

    @Test
    void savesAValidConfiguration() throws Exception {
        when(llmSettingsService.saveSettings(any())).thenReturn(new LlmSettingsService.LlmSettingsSnapshot(
                "https://api.example.com/v1", "companion-model", "prompt", true, Instant.parse("2026-07-17T12:00:00Z")
        ));

        mockMvc.perform(put("/api/v1/settings/llm")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"baseUrl":"https://api.example.com/v1","model":"companion-model","systemPrompt":"prompt","apiKey":"sk-secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"baseUrl":"https://api.example.com/v1","model":"companion-model","systemPrompt":"prompt","apiKeyConfigured":true,"updatedAt":"2026-07-17T12:00:00Z"}
                        """, STRICT));
        verify(llmSettingsService).saveSettings(new LlmSettingsService.UpdateLlmSettingsCommand(
                "https://api.example.com/v1", "companion-model", "prompt", "sk-secret"
        ));
    }

    @Test
    void testsTheSavedProviderWithoutReturningTheApiKey() throws Exception {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(llmRuntimeClientFactory.createChatClient()).thenReturn(chatClient);
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("pong");

        mockMvc.perform(post("/api/v1/settings/llm/test")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"ok\":true,\"message\":\"pong\"}", STRICT));
    }

    @Test
    void returnsASafeServiceUnavailableErrorWhenTheProviderCannotBeReached() throws Exception {
        when(llmRuntimeClientFactory.createChatClient()).thenThrow(new LlmProviderUnavailableException());

        mockMvc.perform(post("/api/v1/settings/llm/test")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("""
                        {"code":"llm_provider_unavailable","message":"模型服务暂时不可用，请稍后重试。"}
                        """, STRICT));
    }

    @Test
    void returnsASafeBadRequestWhenStoredLlmSecretsCannotBeLoaded() throws Exception {
        when(llmRuntimeClientFactory.createChatClient())
                .thenThrow(new InvalidLlmSettingsException("AI configuration could not be loaded"));

        mockMvc.perform(post("/api/v1/settings/llm/test")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"invalid_llm_settings","message":"AI 配置不完整或无效。"}
                        """, STRICT));
    }

    @Test
    void returnsASafeBadRequestWhenTheLlmSettingsAreInvalid() throws Exception {
        when(llmSettingsService.saveSettings(any()))
                .thenThrow(new InvalidLlmSettingsException("provider validation detail"));

        mockMvc.perform(put("/api/v1/settings/llm")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"baseUrl":"https://api.example.com/v1","model":"companion-model","systemPrompt":"prompt"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"invalid_llm_settings","message":"AI 配置不完整或无效。"}
                        """, STRICT));
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(put("/api/v1/settings/llm")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"baseUrl\":\"\",\"model\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"invalid_llm_settings","message":"AI 配置不完整或无效。"}
                        """, STRICT));
    }

    @Test
    void rejectsMalformedLlmSettingsWithTheSameSafeResponse() throws Exception {
        mockMvc.perform(put("/api/v1/settings/llm")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"baseUrl\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"invalid_llm_settings","message":"AI 配置不完整或无效。"}
                        """, STRICT));
    }
}
