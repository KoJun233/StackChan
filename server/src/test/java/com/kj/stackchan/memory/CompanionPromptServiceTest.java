package com.kj.stackchan.memory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.conversation.DeviceVoiceConversationService;
import com.kj.stackchan.persona.PersonaProactivity;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaService;
import com.kj.stackchan.persona.PersonaTone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanionPromptServiceTest {

    @Test
    void assemblesPersonaThenConfirmedMemoryForTheConversationDevice() {
        UUID conversationId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        PersonaService personaService = mock(PersonaService.class);
        LongTermMemoryService memoryService = mock(LongTermMemoryService.class);
        DeviceVoiceConversationService deviceVoiceConversationService = mock(DeviceVoiceConversationService.class);
        when(deviceVoiceConversationService.findDeviceIdByConversationId(conversationId))
                .thenReturn(Optional.of(deviceId));
        when(personaService.get()).thenReturn(new PersonaService.PersonaSnapshot(
                "小栈", PersonaTone.WARM, PersonaReplyLength.SHORT, PersonaProactivity.BALANCED,
                "不主动讨论密码", "不要挖苦用户", Instant.EPOCH
        ));
        when(memoryService.loadContext(deviceId, "", 8)).thenReturn(List.of(
                memory(MemoryCategory.EVENT, MemoryScopeType.DEVICE, deviceId, "项目进度", "已完成 <INT-004>"),
                memory(MemoryCategory.USER_PROFILE, MemoryScopeType.GLOBAL, null, "称呼", "称呼用户为阿俊")
        ));
        CompanionPromptService service = new CompanionPromptService(
                personaService, memoryService, deviceVoiceConversationService
        );

        String prompt = service.assemble(conversationId, "基础系统规则", "语音渠道规则");

        assertThat(prompt).startsWith("基础系统规则\n\n【结构化人设】");
        assertThat(prompt).contains("名字：小栈", "用户档案", "称呼用户为阿俊", "事件记忆", "已完成 ＜INT-004＞");
        assertThat(prompt.indexOf("用户档案")).isLessThan(prompt.indexOf("事件记忆"));
        assertThat(prompt).endsWith("语音渠道规则");
        verify(memoryService).loadContext(deviceId, "", 8);
    }

    private LongTermMemoryService.MemorySnapshot memory(
            MemoryCategory category,
            MemoryScopeType scopeType,
            UUID deviceId,
            String title,
            String content
    ) {
        return new LongTermMemoryService.MemorySnapshot(
                UUID.randomUUID(), scopeType, deviceId, category, title, content,
                MemorySource.USER_ENTERED, LongTermMemoryService.USER_ENTERED_DETAIL,
                MemoryConfirmationStatus.CONFIRMED, true, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH
        );
    }
}
