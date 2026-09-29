package com.kj.stackchan.device;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BodyMotionCommandServiceTest {
    private final BodyMotionCommandRepository repository = mock(BodyMotionCommandRepository.class);
    private final DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
    private final BodyMotionCommandService service = new BodyMotionCommandService(
            repository, gateway, Clock.fixed(Instant.parse("2026-09-23T00:00:00Z"), ZoneOffset.UTC));
    private final UUID deviceId = UUID.randomUUID();

    @Test
    void sendsAStableCommandIdAndRecordsAnAcknowledgementAndFinalResult() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gateway.playBodyMotion(eq(deviceId), eq("NOD_SMALL"), any())).thenReturn(true);
        BodyMotionCommandEntity command = service.issue(deviceId, "NOD_SMALL");
        when(repository.existsByIdAndDeviceId(command.getId(), deviceId)).thenReturn(true);
        assertThat(service.recordAcknowledgement(deviceId, command.getId().toString(), true)).isTrue();
        service.recordResult(deviceId, command.getId().toString(),
                "NOD_SMALL", "COMPLETED", "NONE");
        verify(gateway).playBodyMotion(deviceId, "NOD_SMALL", command.getId().toString());
        verify(repository).markAcknowledged(eq(deviceId), eq(command.getId()),
                eq("ACCEPTED"), any());
        verify(repository).markFinal(eq(deviceId), eq(command.getId()),
                eq("NOD_SMALL"), eq("COMPLETED"), eq("NONE"), any());
    }

    @Test
    void unavailableTransportNeverBecomesAnAcceptedMotion() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByIdAndDeviceId(any(), eq(deviceId))).thenAnswer(invocation ->
                java.util.Optional.of(new BodyMotionCommandEntity(
                        invocation.getArgument(0), deviceId, "WAKE", Instant.parse("2026-09-23T00:00:00Z")))
        );
        BodyMotionCommandEntity command = service.issue(deviceId, "WAKE");
        verify(repository).markDeliveryFailed(eq(deviceId), eq(command.getId()), any());
    }
}
