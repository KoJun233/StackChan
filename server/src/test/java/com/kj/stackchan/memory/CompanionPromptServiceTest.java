package com.kj.stackchan.memory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.conversation.DeviceVoiceConversationService;
import com.kj.stackchan.conversation.ConversationService;
import com.kj.stackchan.persona.PersonaProactivity;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaTone;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.role.CompanionRoleService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CompanionPromptServiceTest {

    @Test
    void assemblesPersonaThenConfirmedMemoryForTheConversationDevice() {
        UUID conversationId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID roleId = CompanionRoleEntity.DEFAULT_ROLE_ID;
        CompanionRoleService roleService = mock(CompanionRoleService.class);
        LongTermMemoryService memoryService = mock(LongTermMemoryService.class);
        DeviceVoiceConversationService deviceVoiceConversationService = mock(DeviceVoiceConversationService.class);
        ConversationService conversationService = mock(ConversationService.class);
        when(deviceVoiceConversationService.findDeviceIdByConversationId(conversationId))
                .thenReturn(Optional.of(deviceId));
        when(conversationService.roleId(conversationId)).thenReturn(roleId);
        when(roleService.get(roleId)).thenReturn(new CompanionRoleService.RoleSnapshot(
                roleId, "小栈", PersonaTone.WARM, PersonaReplyLength.SHORT, PersonaProactivity.BALANCED,
                "", "不主动讨论密码", "不要挖苦用户", true, null, Instant.EPOCH, Instant.EPOCH
        ));
        when(memoryService.loadContext(roleId, deviceId, "", 8)).thenReturn(List.of(
                memory(MemoryCategory.EVENT, MemoryScopeType.DEVICE, deviceId, "项目进度", "已完成 <INT-004>"),
                memory(MemoryCategory.USER_PROFILE, MemoryScopeType.GLOBAL, null, "称呼", "称呼用户为阿俊")
        ));
        CompanionPromptService service = new CompanionPromptService(
                roleService, memoryService, deviceVoiceConversationService, conversationService
        );

        String prompt = service.assemble(conversationId, "基础系统规则", "语音渠道规则");

        assertThat(prompt).startsWith("基础系统规则\n\n【结构化人设】");
        assertThat(prompt).contains("名字：小栈", "用户档案", "称呼用户为阿俊", "事件记忆", "已完成 ＜INT-004＞");
        assertThat(prompt.indexOf("<用户档案>")).isLessThan(prompt.indexOf("<事件记忆>"));
        assertThat(prompt).endsWith("语音渠道规则");
        verify(memoryService).loadContext(roleId, deviceId, "", 8);
    }

    @Test
    void tracksOnlyWholeMemoriesActuallyRenderedWithinTheBudget() {
        var oversized = memory(MemoryCategory.USER_PROFILE, MemoryScopeType.GLOBAL, null,
                "过长条目", "大".repeat(4_000));
        var first = memory(MemoryCategory.USER_PROFILE, MemoryScopeType.GLOBAL, null,
                "有效档案一", "甲".repeat(1_850));
        var second = memory(MemoryCategory.USER_PROFILE, MemoryScopeType.GLOBAL, null,
                "有效档案二", "乙".repeat(1_850));
        var excluded = memory(MemoryCategory.EVENT, MemoryScopeType.GLOBAL, null,
                "被预算排除的事件", "丙".repeat(500));
        var small = memory(MemoryCategory.EVENT, MemoryScopeType.GLOBAL, null,
                "短事件", "完成联调");

        var assembly = assemble(List.of(oversized, first, second, excluded, small));

        assertThat(assembly.memoryIds()).containsExactly(first.id(), second.id(), small.id());
        String rendered = assembly.prompt().substring(assembly.prompt().indexOf("<用户档案>"));
        assertThat(rendered.length()).isLessThanOrEqualTo(4_000);
        assertThat(rendered).contains("有效档案一", "有效档案二", "短事件", "</用户档案>", "</事件记忆>")
                .doesNotContain("过长条目", "被预算排除的事件", "丙");
    }

    @Test
    void emptyOrUnrenderableContextDoesNotClaimThatNoStoredMemoriesExist() {
        var oversized = memory(MemoryCategory.EVENT, MemoryScopeType.GLOBAL, null,
                "超长事件", "长".repeat(4_000));
        for (var candidates : List.of(List.<LongTermMemoryService.MemorySnapshot>of(), List.of(oversized))) {
            var assembly = assemble(candidates);
            assertThat(assembly.memoryIds()).isEmpty();
            assertThat(assembly.prompt()).contains("本轮没有可引用", "不代表全部存储记录")
                    .doesNotContain("当前没有已确认且启用的长期记忆", "<事件记忆>", "超长事件");
        }
    }

    @Test
    void continuationPermissionTracksItsActualMemoryReferenceEvenOutsideOrdinaryRecall() {
        UUID conversation = UUID.randomUUID(), device = UUID.randomUUID(), role = UUID.randomUUID(), topic = UUID.randomUUID();
        var roles = mock(CompanionRoleService.class);
        var memories = mock(LongTermMemoryService.class);
        var devices = mock(DeviceVoiceConversationService.class);
        var conversations = mock(ConversationService.class);
        var topics = mock(TopicContinuationService.class);
        when(devices.findDeviceIdByConversationId(conversation)).thenReturn(Optional.of(device));
        when(conversations.roleId(conversation)).thenReturn(role);
        when(roles.get(role)).thenReturn(new CompanionRoleService.RoleSnapshot(role, "小栈", PersonaTone.WARM,
                PersonaReplyLength.SHORT, PersonaProactivity.BALANCED, "", "", "", true, null, Instant.EPOCH, Instant.EPOCH));
        when(topics.assembleContext(role, device, conversation, null, "木工"))
                .thenReturn(new TopicContinuationService.ContinuationContext(topic, "\n本轮允许接续：木工"));
        var service = new CompanionPromptService(roles, memories, devices, conversations);
        service.setContinuationService(topics);
        var assembly = service.assembleWithMemoryContext(conversation, "基础规则", "语音渠道规则", "木工");
        assertThat(assembly.memoryIds()).containsExactly(topic);
        assertThat(assembly.prompt()).contains("本轮允许接续：木工", "已暂停、停止的话题不得").endsWith("语音渠道规则");
    }

    @Test
    void webConversationKeepsConfirmedMemoryWithoutContinuationPermissionEvenWhenDeviceBound() {
        UUID conversation = UUID.randomUUID(), device = UUID.randomUUID(), role = UUID.randomUUID();
        var roles = mock(CompanionRoleService.class);
        var memories = mock(LongTermMemoryService.class);
        var devices = mock(DeviceVoiceConversationService.class);
        var conversations = mock(ConversationService.class);
        var topics = mock(TopicContinuationService.class);
        var event = memory(MemoryCategory.EVENT, MemoryScopeType.DEVICE, device, "木工", "底座已经完成");
        var profile = memory(MemoryCategory.USER_PROFILE, MemoryScopeType.GLOBAL, null, "称呼", "用户喜欢被称为阿俊");
        when(devices.findDeviceIdByConversationId(conversation)).thenReturn(Optional.of(device));
        when(conversations.roleId(conversation)).thenReturn(role);
        when(roles.get(role)).thenReturn(new CompanionRoleService.RoleSnapshot(role, "小栈", PersonaTone.WARM,
                PersonaReplyLength.SHORT, PersonaProactivity.BALANCED, "", "", "", true, null, Instant.EPOCH, Instant.EPOCH));
        when(memories.loadContext(role, device, "木工", 8)).thenReturn(List.of(event, profile));
        var service = new CompanionPromptService(roles, memories, devices, conversations);
        service.setContinuationService(topics);

        var assembly = service.assembleWebWithMemoryContext(conversation, "基础规则", "网页渠道规则", "木工");

        assertThat(assembly.memoryIds()).containsExactly(profile.id(), event.id());
        assertThat(assembly.prompt()).contains("底座已经完成", "用户喜欢被称为阿俊", "事件记忆只用于回答当前用户问题")
                .doesNotContain("【本轮允许自然接续的话题】", "用户上次明确说的进展").endsWith("网页渠道规则");
        verify(memories).loadContext(role, device, "木工", 8);
        verifyNoInteractions(topics);
        verify(conversations, never()).voiceTopicBoundary(conversation);
    }

    private CompanionPromptService.PromptAssembly assemble(List<LongTermMemoryService.MemorySnapshot> memories) {
        UUID conversation = UUID.randomUUID();
        UUID role = UUID.randomUUID();
        var roles = mock(CompanionRoleService.class);
        var memoryService = mock(LongTermMemoryService.class);
        var devices = mock(DeviceVoiceConversationService.class);
        var conversations = mock(ConversationService.class);
        when(conversations.roleId(conversation)).thenReturn(role);
        when(roles.get(role)).thenReturn(new CompanionRoleService.RoleSnapshot(
                role, "小栈", PersonaTone.WARM, PersonaReplyLength.SHORT, PersonaProactivity.BALANCED,
                "", "", "", true, null, Instant.EPOCH, Instant.EPOCH));
        when(memoryService.loadContext(role, null, "你记得什么", 8)).thenReturn(memories);
        return new CompanionPromptService(roles, memoryService, devices, conversations)
                .assembleWithMemoryContext(conversation, "基础规则", "", "你记得什么");
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
