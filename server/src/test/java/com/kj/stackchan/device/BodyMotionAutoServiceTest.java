package com.kj.stackchan.device;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.interaction.InteractionSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BodyMotionAutoServiceTest {
    private final UUID deviceId = UUID.randomUUID();
    private final BodyMotionAutoSettingsRepository settings = mock(BodyMotionAutoSettingsRepository.class);
    private final BodyMotionCommandRepository commands = mock(BodyMotionCommandRepository.class);
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
    private final InteractionSettingsService interaction = mock(InteractionSettingsService.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC);
    private final BodyMotionAutoService service = new BodyMotionAutoService(
            settings, commands, devices, gateway, interaction, clock, manager);

    @BeforeEach
    void setUp() {
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(settings.lockByDeviceId(deviceId)).thenReturn(
                Optional.of(new BodyMotionAutoSettingsEntity(deviceId, true)));
        when(gateway.isConnected(deviceId)).thenReturn(true);
        when(gateway.playBodyMotion(eq(deviceId), eq("WAKE"), any())).thenReturn(true);
        DeviceEntity device = mock(DeviceEntity.class);
        when(device.getSafetyState()).thenReturn(DeviceEventService.MOTION_ARMED);
        when(device.getBodyDiagnostics()).thenReturn(new DeviceBodyDiagnostics(
                true, true, true, true, true, true, true,
                "NORMAL", "ARMED", "NONE", 0));
        when(devices.findById(deviceId)).thenReturn(Optional.of(device));
        var resolved = mock(InteractionSettingsService.InteractionSettingsSnapshot.class);
        when(resolved.zoneId()).thenReturn("Asia/Shanghai");
        when(interaction.resolve(deviceId)).thenReturn(resolved);
        when(commands.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void defaultsOffAndRequiresAConnectedArmedDevice() {
        when(settings.lockByDeviceId(deviceId)).thenReturn(Optional.empty());
        assertThat(service.request(deviceId, "WAKE", "workday-start:1")).isFalse();
        when(settings.lockByDeviceId(deviceId)).thenReturn(
                Optional.of(new BodyMotionAutoSettingsEntity(deviceId, true)));
        when(gateway.isConnected(deviceId)).thenReturn(false);
        assertThat(service.request(deviceId, "WAKE", "workday-start:1")).isFalse();
    }

    @Test
    void sendsOneImmediateFixedMotionAndRejectsDuplicateEvent() {
        assertThat(service.request(deviceId, "WAKE", "workday-start:1")).isTrue();
        verify(gateway).playBodyMotion(eq(deviceId), eq("WAKE"), any());
        when(commands.existsByDeviceIdAndEventKey(deviceId, "workday-start:1")).thenReturn(true);
        assertThat(service.request(deviceId, "WAKE", "workday-start:1")).isFalse();
    }

    @Test
    void completedDailyQuotaStopsFurtherAutomaticMotions() {
        when(commands.countByDeviceIdAndAutomaticTrueAndStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
                eq(deviceId), eq("COMPLETED"), any(), any())).thenReturn(8L);
        assertThat(service.request(deviceId, "WAKE", "workday-start:2")).isFalse();
    }

    @Test
    void lostExecutionResultDisablesAutomaticMotionUntilReviewed() {
        var setting = new BodyMotionAutoSettingsEntity(deviceId, true);
        var oldCommand = new BodyMotionCommandEntity(UUID.randomUUID(), deviceId, "WAKE", true,
                "workday-start:old", clock.instant().minusSeconds(20));
        when(settings.lockByDeviceId(deviceId)).thenReturn(Optional.of(setting));
        when(commands.findAllByDeviceIdAndAutomaticTrueAndStatusIn(
                deviceId, List.of("SENT", "ACCEPTED"))).thenReturn(List.of(oldCommand));
        assertThat(service.request(deviceId, "WAKE", "workday-start:new")).isFalse();
        assertThat(setting.isEnabled()).isFalse();
        verify(commands).markUnconfirmed(eq(deviceId), eq(oldCommand.getId()), any(), any());
    }

    @Test
    void scheduledExpiryDisablesAutomaticMotionWithoutAnotherEvent() {
        var setting = new BodyMotionAutoSettingsEntity(deviceId, true);
        var oldCommand = new BodyMotionCommandEntity(UUID.randomUUID(), deviceId, "THINK", true,
                "web-think:old", clock.instant().minusSeconds(20));
        when(settings.lockByDeviceId(deviceId)).thenReturn(Optional.of(setting));
        when(commands.findTop100ByAutomaticTrueAndStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(List.of("SENT", "ACCEPTED")), any())).thenReturn(List.of(oldCommand));
        when(commands.markUnconfirmed(eq(deviceId), eq(oldCommand.getId()), any(), any())).thenReturn(1);

        service.expireMissingResults();

        assertThat(setting.isEnabled()).isFalse();
        verify(settings).save(setting);
        verify(commands).markUnconfirmed(eq(deviceId), eq(oldCommand.getId()), any(), any());
    }

    @Test
    void scheduledExpiryDoesNotDisableAfterAResultWinsTheRace() {
        var setting = new BodyMotionAutoSettingsEntity(deviceId, true);
        var oldCommand = new BodyMotionCommandEntity(UUID.randomUUID(), deviceId, "THINK", true,
                "web-think:old", clock.instant().minusSeconds(20));
        when(settings.lockByDeviceId(deviceId)).thenReturn(Optional.of(setting));
        when(commands.findTop100ByAutomaticTrueAndStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(List.of("SENT", "ACCEPTED")), any())).thenReturn(List.of(oldCommand));

        service.expireMissingResults();

        assertThat(setting.isEnabled()).isTrue();
        org.mockito.Mockito.verify(settings, org.mockito.Mockito.never()).save(setting);
    }
}
