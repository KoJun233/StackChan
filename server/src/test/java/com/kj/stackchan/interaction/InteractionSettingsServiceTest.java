package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractionSettingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T15:00:00Z");

    @Mock
    private DeviceInteractionSettingsRepository repository;
    @Mock
    private DeviceRepository deviceRepository;

    @Test
    void appliesCrossMidnightDndAndFindsItsEnd() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(repository.findById(deviceId)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        InteractionSettingsService service = service();

        var settings = service.save(deviceId, new InteractionSettingsService.UpdateInteractionSettingsCommand(
                50, false, true, LocalTime.of(22, 0), LocalTime.of(7, 0), "Asia/Shanghai",
                MissedReminderPolicy.PLAY_NOW, 10, false, LocalTime.of(9, 0), LocalTime.of(21, 0),
                240, 2, "你好"
        ));

        assertThat(service.isDnd(settings, NOW)).isTrue();
        assertThat(service.nextDndEnd(settings, NOW)).isEqualTo(Instant.parse("2026-07-27T23:00:00Z"));
    }

    @Test
    void proactiveGreetingRespectsDailyLimit() {
        UUID deviceId = UUID.randomUUID();
        var settings = new InteractionSettingsService.InteractionSettingsSnapshot(
                deviceId, 50, false, false, LocalTime.of(22, 0), LocalTime.of(7, 0), "UTC",
                MissedReminderPolicy.PLAY_NOW, 10, true, LocalTime.of(9, 0), LocalTime.of(21, 0),
                60, 2, "你好", NOW.minusSeconds(7200), NOW.atZone(ZoneOffset.UTC).toLocalDate(),
                2, NOW
        );

        assertThat(service().isProactiveEligible(settings, NOW)).isFalse();
    }

    private InteractionSettingsService service() {
        return new InteractionSettingsService(
                repository, deviceRepository, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
