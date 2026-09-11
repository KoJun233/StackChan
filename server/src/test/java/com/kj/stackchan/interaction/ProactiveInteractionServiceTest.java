package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ProactiveGenerationStatus;
import com.kj.stackchan.role.CompanionRoleService;
import com.kj.stackchan.speech.VoiceTurnRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProactiveInteractionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T12:30:00Z");

    @Mock private InteractionSettingsService settingsService;
    @Mock private ReminderRepository reminderRepository;
    @Mock private VoiceTurnRepository voiceTurnRepository;
    @Mock private DeviceCommandGateway commandGateway;
    @Mock private LongTermMemoryService memoryService;
    @Mock private ProactiveTopicCooldownService topicCooldownService;
    @Mock private ProactiveMessageGenerator messageGenerator;
    @Mock private CompanionRoleService roleService;
    @Mock private ProactiveInterestBriefSource interestBriefSource;

    @Test
    void blocksOnlyOnRecentlyUpdatedVoiceTurns() {
        UUID deviceId = UUID.randomUUID();
        var settings = new InteractionSettingsService.InteractionSettingsSnapshot(
                deviceId, 50, false, false, 8, false, LocalTime.of(22, 0), LocalTime.of(7, 0), "UTC",
                MissedReminderPolicy.PLAY_NOW, 10, true, LocalTime.MIN, LocalTime.MAX,
                30, 2, "hello", null, LocalDate.of(2026, 7, 27), 0, NOW
        );
        when(settingsService.proactiveCandidates()).thenReturn(List.of(settings));
        when(settingsService.isProactiveEligible(settings, NOW)).thenReturn(true);
        when(commandGateway.isConnected(deviceId)).thenReturn(true);
        when(voiceTurnRepository.existsByDeviceIdAndStatusInAndUpdatedAtAfter(
                eq(deviceId), any(), eq(NOW.minusSeconds(15 * 60))
        )).thenReturn(true);

        int generated = service().generateDueGreetings();

        assertThat(generated).isZero();
        verify(voiceTurnRepository).existsByDeviceIdAndStatusInAndUpdatedAtAfter(
                eq(deviceId), any(), eq(NOW.minusSeconds(15 * 60))
        );
        verifyNoInteractions(memoryService, topicCooldownService, messageGenerator);
    }

    @Test
    void generatesFromOneAllowedMemoryOnlyAfterAllRulesPass() {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        var settings = new InteractionSettingsService.InteractionSettingsSnapshot(
                deviceId, 50, false, false, 8, false, LocalTime.of(22, 0), LocalTime.of(7, 0), "UTC",
                MissedReminderPolicy.PLAY_NOW, 10, true, LocalTime.MIN, LocalTime.MAX,
                30, 2, "hello", null, LocalDate.of(2026, 7, 27), 0, NOW, null, true
        );
        LongTermMemoryService.MemorySnapshot memory = org.mockito.Mockito.mock(LongTermMemoryService.MemorySnapshot.class);
        CompanionRoleService.RoleSnapshot role = org.mockito.Mockito.mock(CompanionRoleService.RoleSnapshot.class);
        when(role.id()).thenReturn(roleId);
        when(memory.topicKey()).thenReturn("coffee");
        when(roleService.getActive(deviceId)).thenReturn(role);
        when(settingsService.proactiveCandidates()).thenReturn(List.of(settings));
        when(settingsService.isProactiveEligible(settings, NOW)).thenReturn(true);
        when(settingsService.recordProactiveIfEligible(deviceId, NOW)).thenReturn(true);
        when(commandGateway.isConnected(deviceId)).thenReturn(true);
        when(memoryService.loadProactiveCandidates(roleId, deviceId, 8)).thenReturn(List.of(memory));
        when(topicCooldownService.isEligible(deviceId, roleId, "coffee", NOW)).thenReturn(true);
        when(messageGenerator.generate("hello", memory, role)).thenReturn(
                new ProactiveMessageGenerator.GenerationResult("来杯美式，也别忘了休息一下。", ProactiveGenerationStatus.GENERATED)
        );

        assertThat(serviceWithRole().generateDueGreetings()).isEqualTo(1);

        ArgumentCaptor<com.kj.stackchan.reminder.ReminderEntity> reminder =
                ArgumentCaptor.forClass(com.kj.stackchan.reminder.ReminderEntity.class);
        verify(reminderRepository).save(reminder.capture());
        assertThat(reminder.getValue().getRoleId()).isEqualTo(roleId);
        assertThat(reminder.getValue().getProactiveTopicKey()).isEqualTo("coffee");
        assertThat(reminder.getValue().getProactiveGenerationStatus()).isEqualTo(ProactiveGenerationStatus.GENERATED);
        verify(topicCooldownService).recordMention(deviceId, roleId, "coffee", NOW);
    }

    @Test
    void storesTheExactEvidenceForASelectedInterestBrief() {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        var settings = new InteractionSettingsService.InteractionSettingsSnapshot(
                deviceId, 50, false, false, 8, false, LocalTime.of(22, 0), LocalTime.of(7, 0), "UTC",
                MissedReminderPolicy.PLAY_NOW, 10, true, LocalTime.MIN, LocalTime.MAX,
                60, 3, "hello", null, LocalDate.of(2026, 7, 27), 0, NOW, null, true
        );
        LongTermMemoryService.MemorySnapshot memory = org.mockito.Mockito.mock(LongTermMemoryService.MemorySnapshot.class);
        CompanionRoleService.RoleSnapshot role = org.mockito.Mockito.mock(CompanionRoleService.RoleSnapshot.class);
        InterestBrief brief = new InterestBrief(
                "Hacker News", "New AI research", "https://example.com/paper",
                NOW.minusSeconds(3600), NOW.minusSeconds(60)
        );
        when(role.id()).thenReturn(roleId);
        when(memory.topicKey()).thenReturn("ai");
        when(roleService.getActive(deviceId)).thenReturn(role);
        when(settingsService.proactiveCandidates()).thenReturn(List.of(settings));
        when(settingsService.isProactiveEligible(settings, NOW)).thenReturn(true);
        when(settingsService.recordProactiveIfEligible(deviceId, NOW)).thenReturn(true);
        when(commandGateway.isConnected(deviceId)).thenReturn(true);
        when(memoryService.loadProactiveCandidates(roleId, deviceId, 8)).thenReturn(List.of(memory));
        when(topicCooldownService.isEligible(deviceId, roleId, "ai", NOW)).thenReturn(true);
        when(interestBriefSource.current()).thenReturn(List.of(brief));
        when(messageGenerator.generate("hello", memory, role, List.of(brief))).thenReturn(
                new ProactiveMessageGenerator.GenerationResult("有一条 AI 资讯，想听吗？", ProactiveGenerationStatus.GENERATED, brief)
        );

        assertThat(serviceWithSources().generateDueGreetings()).isEqualTo(1);

        ArgumentCaptor<com.kj.stackchan.reminder.ReminderEntity> reminder =
                ArgumentCaptor.forClass(com.kj.stackchan.reminder.ReminderEntity.class);
        verify(reminderRepository).save(reminder.capture());
        assertThat(reminder.getValue().getProactiveSourceName()).isEqualTo("Hacker News");
        assertThat(reminder.getValue().getProactiveSourceTitle()).isEqualTo("New AI research");
        assertThat(reminder.getValue().getProactiveSourceUrl()).isEqualTo("https://example.com/paper");
        assertThat(reminder.getValue().getProactiveSourcePublishedAt()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(reminder.getValue().getProactiveSourceRetrievedAt()).isEqualTo(NOW.minusSeconds(60));
    }

    private ProactiveInteractionService service() {
        return new ProactiveInteractionService(
                settingsService,
                reminderRepository,
                voiceTurnRepository,
                commandGateway,
                memoryService,
                topicCooldownService,
                messageGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private ProactiveInteractionService serviceWithRole() {
        return new ProactiveInteractionService(
                settingsService,
                reminderRepository,
                voiceTurnRepository,
                commandGateway,
                memoryService,
                topicCooldownService,
                messageGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC),
                roleService
        );
    }

    private ProactiveInteractionService serviceWithSources() {
        return new ProactiveInteractionService(
                settingsService, reminderRepository, voiceTurnRepository, commandGateway, memoryService,
                topicCooldownService, messageGenerator, Clock.fixed(NOW, ZoneOffset.UTC), roleService,
                interestBriefSource
        );
    }
}
