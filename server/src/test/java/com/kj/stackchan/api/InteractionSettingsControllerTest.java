package com.kj.stackchan.api;

import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceInteractionSettingsCoordinator;
import com.kj.stackchan.interaction.InteractionSettingsService;
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

    private InteractionSettingsController controller() {
        return new InteractionSettingsController(
                settingsService,
                settingsCoordinator,
                commandGateway,
                diagnosticsService
        );
    }
}
