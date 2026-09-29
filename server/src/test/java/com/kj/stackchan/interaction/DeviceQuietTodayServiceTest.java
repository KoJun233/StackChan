package com.kj.stackchan.interaction;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceQuietTodayServiceTest {
    @Test
    void quietTodayExpiresAtTheDevicesNextLocalMidnight() {
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-23T14:00:00Z");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InteractionSettingsService settings = mock(InteractionSettingsService.class);
        DeviceRepository devices = mock(DeviceRepository.class);
        var snapshot = mock(InteractionSettingsService.InteractionSettingsSnapshot.class);
        when(devices.existsById(deviceId)).thenReturn(true);
        when(settings.resolve(deviceId)).thenReturn(snapshot);
        when(snapshot.zoneId()).thenReturn("Asia/Shanghai");
        var service = new DeviceQuietTodayService(jdbc, Clock.fixed(now, ZoneOffset.UTC), settings, devices);

        DeviceQuietTodayService.Snapshot paused = service.quietForToday(deviceId);

        assertThat(paused.quiet()).isTrue();
        assertThat(paused.pausedUntil()).isEqualTo(Instant.parse("2026-09-23T16:00:00Z"));
        ArgumentCaptor<Timestamp> until = ArgumentCaptor.forClass(Timestamp.class);
        verify(jdbc).update(any(String.class), eq(deviceId), until.capture(), eq(Timestamp.from(now)));
        assertThat(until.getValue().toInstant()).isEqualTo(paused.pausedUntil());
    }
}
