package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.expression.CompanionEmotion;
import com.kj.stackchan.expression.DeviceExpressionService;
import com.kj.stackchan.expression.ExpressionSuggestionParser;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.role.CompanionRoleService;
import com.kj.stackchan.speech.VoiceTurnRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SilentPresenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");

    @Mock private InteractionSettingsService settingsService;
    @Mock private DeviceCommandGateway commandGateway;
    @Mock private VoiceTurnRepository voiceTurnRepository;
    @Mock private ReminderRepository reminderRepository;
    @Mock private DeviceExpressionService expressionService;
    @Mock private CompanionRoleService roleService;

    @Test
    void showsAContentExpressionAndRecordsItOnlyAfterAllRulesPass() {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        var settings = settings(deviceId);
        var role = org.mockito.Mockito.mock(CompanionRoleService.RoleSnapshot.class);
        when(settingsService.silentPresenceCandidates()).thenReturn(List.of(settings));
        when(settingsService.isSilentPresenceEligible(settings, NOW)).thenReturn(true);
        when(commandGateway.isConnected(deviceId)).thenReturn(true);
        when(roleService.getActive(deviceId)).thenReturn(role);
        when(role.id()).thenReturn(roleId);
        when(expressionService.apply(any(), any(), any())).thenReturn(true);
        when(settingsService.recordSilentPresenceIfEligible(deviceId, NOW)).thenReturn(true);

        int shown = service().showDueExpressions();

        assertThat(shown).isEqualTo(1);
        ArgumentCaptor<ExpressionSuggestionParser.Suggestion> suggestion =
                ArgumentCaptor.forClass(ExpressionSuggestionParser.Suggestion.class);
        verify(expressionService).apply(org.mockito.ArgumentMatchers.eq(deviceId),
                org.mockito.ArgumentMatchers.eq(roleId), suggestion.capture());
        assertThat(suggestion.getValue().emotion()).isEqualTo(CompanionEmotion.CONTENT);
        verify(settingsService).recordSilentPresenceIfEligible(deviceId, NOW);
    }

    @Test
    void staysSilentWhileARecentVoiceTurnIsActive() {
        UUID deviceId = UUID.randomUUID();
        var settings = settings(deviceId);
        when(settingsService.silentPresenceCandidates()).thenReturn(List.of(settings));
        when(settingsService.isSilentPresenceEligible(settings, NOW)).thenReturn(true);
        when(commandGateway.isConnected(deviceId)).thenReturn(true);
        when(voiceTurnRepository.existsByDeviceIdAndStatusInAndUpdatedAtAfter(
                org.mockito.ArgumentMatchers.eq(deviceId), any(), any())).thenReturn(true);

        assertThat(service().showDueExpressions()).isZero();

        verify(expressionService, never()).apply(any(), any(), any());
        verify(settingsService, never()).recordSilentPresenceIfEligible(any(), any());
    }

    private SilentPresenceService service() {
        return new SilentPresenceService(
                settingsService, commandGateway, voiceTurnRepository, reminderRepository,
                expressionService, roleService, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private InteractionSettingsService.InteractionSettingsSnapshot settings(UUID deviceId) {
        return new InteractionSettingsService.InteractionSettingsSnapshot(
                deviceId, 50, false, false, 8, false,
                LocalTime.of(22, 0), LocalTime.of(7, 0), "UTC",
                MissedReminderPolicy.PLAY_NOW, 10, false, LocalTime.of(9, 0), LocalTime.of(21, 0),
                60, 3, "你好", null, null, 0, NOW, null, false, null,
                true, NOW.minusSeconds(1), null, LocalDate.of(2026, 9, 11), 0
        );
    }
}
