package com.kj.stackchan.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.conversation.ConversationMessageSnapshot;
import com.kj.stackchan.conversation.GenerationStatus;
import com.kj.stackchan.conversation.MessageRole;
import com.kj.stackchan.conversation.PersonalDataService;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonalDataController.class)
@Import(SecurityConfiguration.class)
class PersonalDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonalDataService personalDataService;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void requiresAuthenticationForPersonalData() throws Exception {
        mockMvc.perform(get("/api/v1/personal-data/conversations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsMessagesAndDeletesWithCsrfProtection() throws Exception {
        UUID conversationId = UUID.fromString("40253586-dd34-45a3-a985-031356f10d5f");
        UUID messageId = UUID.fromString("218ee63f-f567-43fa-93ea-4f6d43c3e0a5");
        when(personalDataService.list(any(), eq(0), eq(20)))
                .thenReturn(new PersonalDataService.ConversationPage(List.of(), 0));
        when(personalDataService.messages(conversationId)).thenReturn(List.of(new ConversationMessageSnapshot(
                messageId, MessageRole.USER, "只属于用户的数据", GenerationStatus.COMPLETED,
                Instant.parse("2026-07-29T12:00:00Z"), Instant.parse("2026-07-29T12:00:00Z")
        )));

        mockMvc.perform(get("/api/v1/personal-data/conversations")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/v1/personal-data/conversations/{id}/messages", conversationId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("只属于用户的数据"));
        mockMvc.perform(delete("/api/v1/personal-data/conversations/{conversationId}/messages/{messageId}", conversationId, messageId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/personal-data/conversations/{conversationId}/messages/{messageId}", conversationId, messageId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        verify(personalDataService).deleteMessage(conversationId, messageId);
    }

    @Test
    void exportsOnlyTheServiceContractAsAttachment() throws Exception {
        UUID conversationId = UUID.fromString("09c356d6-70ca-4c0c-85a6-e48fd35318b6");
        PersonalDataService.ConversationSummary summary = new PersonalDataService.ConversationSummary(
                conversationId, "导出范围", null, null, 1,
                Instant.parse("2026-07-29T12:00:00Z"), Instant.parse("2026-07-29T12:00:00Z")
        );
        when(personalDataService.export(any(), eq(conversationId))).thenReturn(new PersonalDataService.ConversationExport(
                1,
                Instant.parse("2026-07-29T12:34:56Z"),
                new PersonalDataService.ExportFilter("", null, null, null, conversationId),
                List.of(new PersonalDataService.ExportedConversation(summary, List.of()))
        ));

        mockMvc.perform(get("/api/v1/personal-data/conversations:export")
                        .param("conversationId", conversationId.toString())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("stackchan-conversations-20260729-123456.json")))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"schemaVersion\" : 1")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("apiKey"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jwt"))));
    }
}
