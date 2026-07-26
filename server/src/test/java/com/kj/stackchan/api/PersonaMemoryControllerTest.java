package com.kj.stackchan.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.memory.MemoryCategory;
import com.kj.stackchan.memory.MemoryConfirmationStatus;
import com.kj.stackchan.memory.MemoryScopeType;
import com.kj.stackchan.memory.MemorySource;
import com.kj.stackchan.persona.PersonaProactivity;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaService;
import com.kj.stackchan.persona.PersonaTone;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PersonaController.class, LongTermMemoryController.class})
@Import(SecurityConfiguration.class)
class PersonaMemoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonaService personaService;
    @MockitoBean
    private LongTermMemoryService memoryService;
    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void readsAndUpdatesStructuredPersonaForAdministrator() throws Exception {
        when(personaService.get()).thenReturn(persona("StackChan"));
        when(personaService.save(any())).thenReturn(persona("小栈"));

        mockMvc.perform(get("/api/v1/persona").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("StackChan"));

        mockMvc.perform(put("/api/v1/persona")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"displayName":"小栈","tone":"WARM","replyLength":"SHORT",\
                                "proactivity":"BALANCED","topicBoundaries":"","taboos":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("小栈"));
    }

    @Test
    void listsPendingSuggestionsAndConfirmsOneExplicitly() throws Exception {
        UUID memoryId = UUID.randomUUID();
        LongTermMemoryService.MemorySnapshot pending = memory(
                memoryId, MemoryConfirmationStatus.PENDING, false
        );
        when(memoryService.list("里程碑", null, MemoryConfirmationStatus.PENDING, null, null, null, 0, 20))
                .thenReturn(new LongTermMemoryService.MemoryPage(List.of(pending), 1));
        when(memoryService.confirm(memoryId)).thenReturn(memory(
                memoryId, MemoryConfirmationStatus.CONFIRMED, true
        ));

        mockMvc.perform(get("/api/v1/memories")
                        .with(user("admin").roles("ADMIN"))
                        .param("query", "里程碑")
                        .param("confirmationStatus", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list[0].enabled").value(false));

        mockMvc.perform(post("/api/v1/memories/{id}:confirm", memoryId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(memoryService).confirm(memoryId);
    }

    private PersonaService.PersonaSnapshot persona(String name) {
        return new PersonaService.PersonaSnapshot(
                name, PersonaTone.WARM, PersonaReplyLength.SHORT, PersonaProactivity.BALANCED,
                "", "", Instant.EPOCH
        );
    }

    private LongTermMemoryService.MemorySnapshot memory(
            UUID id,
            MemoryConfirmationStatus status,
            boolean enabled
    ) {
        return new LongTermMemoryService.MemorySnapshot(
                id, MemoryScopeType.GLOBAL, null, MemoryCategory.EVENT,
                "项目里程碑", "完成第一轮联调", MemorySource.ASSISTANT_SUGGESTED,
                "机器人根据对话提出，等待用户确认", status, enabled,
                status == MemoryConfirmationStatus.CONFIRMED ? Instant.EPOCH : null,
                Instant.EPOCH, Instant.EPOCH
        );
    }
}
