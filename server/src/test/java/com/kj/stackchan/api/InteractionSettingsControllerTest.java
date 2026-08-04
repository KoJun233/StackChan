package com.kj.stackchan.api;

import java.time.LocalTime;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceInteractionSettingsCoordinator;
import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.interaction.MissedReminderPolicy;
import com.kj.stackchan.interaction.ProactiveTopicCooldownService;
import com.kj.stackchan.speech.VoiceTurnDiagnosticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractionSettingsControllerTest {

    @Mock private InteractionSettingsService settingsService;
    @Mock private DeviceInteractionSettingsCoordinator settingsCoordinator;
    @Mock private DeviceCommandGateway commandGateway;
    @Mock private VoiceTurnDiagnosticsService diagnosticsService;
    @Mock private ProactiveTopicCooldownService topicCooldownService;

    @Test
    void cancelsActiveTurnsAfterTheDeviceAcceptsStop() {
        UUID deviceId = UUID.randomUUID();
        when(commandGateway.stopAudio(deviceId)).thenReturn(true);

        var response = controller().stop(deviceId);

        assertThat(response.accepted()).isTrue();
        verify(diagnosticsService).cancelActiveTurns(deviceId);
    }

    @Test
    void keepsActiveTurnsWhenStopCannotReachTheDevice() {
        UUID deviceId = UUID.randomUUID();

        var response = controller().stop(deviceId);

        assertThat(response.accepted()).isFalse();
        verify(diagnosticsService, never()).cancelActiveTurns(deviceId);
    }

    @Test
    void mapsBoundedContinuousConversationSettings() {
        var command = new InteractionSettingsController.InteractionSettingsRequest(
                65, false, true, 6, false, LocalTime.of(22, 0), LocalTime.of(7, 0),
                "Asia/Shanghai", MissedReminderPolicy.PLAY_NOW, 10, false,
                LocalTime.of(9, 0), LocalTime.of(21, 0), 240, 2, "你好", true
        ).toCommand();

        assertThat(command.continuousConversationEnabled()).isTrue();
        assertThat(command.followUpWindowSeconds()).isEqualTo(6);
        assertThat(command.proactivePersonalizationEnabled()).isTrue();
    }

    private InteractionSettingsController controller() {
        return new InteractionSettingsController(
                settingsService,
                settingsCoordinator,
                commandGateway,
                diagnosticsService,
                topicCooldownService
        );
    }
}
