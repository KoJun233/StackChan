package com.kj.stackchan.interaction;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceQuietTodayService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final InteractionSettingsService settings;
    private final DeviceRepository devices;

    public DeviceQuietTodayService(JdbcTemplate jdbc, Clock clock,
                                   InteractionSettingsService settings, DeviceRepository devices) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
        this.devices = devices;
    }

    @Transactional(readOnly = true)
    public boolean isQuiet(UUID deviceId, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from device_quiet_today
                where device_id = ? and paused_until > ?)
                """, Boolean.class, deviceId, Timestamp.from(now)));
    }

    @Transactional(readOnly = true)
    public Snapshot get(UUID deviceId) {
        validate(deviceId);
        var rows = jdbc.query("select paused_until from device_quiet_today where device_id = ?",
                (row, index) -> row.getTimestamp(1).toInstant(), deviceId);
        Instant until = rows.isEmpty() ? null : rows.getFirst();
        return new Snapshot(deviceId, until, until != null && until.isAfter(clock.instant()));
    }

    @Transactional
    public Snapshot quietForToday(UUID deviceId) {
        validate(deviceId);
        Instant now = clock.instant();
        ZoneId zone = ZoneId.of(settings.resolve(deviceId).zoneId());
        Instant until = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
        jdbc.update("""
                insert into device_quiet_today(device_id, paused_until, updated_at) values (?, ?, ?)
                on conflict (device_id) do update
                set paused_until = excluded.paused_until, updated_at = excluded.updated_at
                """, deviceId, Timestamp.from(until), Timestamp.from(now));
        return new Snapshot(deviceId, until, true);
    }

    @Transactional
    public Snapshot resume(UUID deviceId) {
        validate(deviceId);
        jdbc.update("delete from device_quiet_today where device_id = ?", deviceId);
        return new Snapshot(deviceId, null, false);
    }

    private void validate(UUID deviceId) {
        if (deviceId == null || !devices.existsById(deviceId))
            throw new InvalidInteractionSettingsException("Quiet device is invalid");
    }

    public record Snapshot(UUID deviceId, Instant pausedUntil, boolean quiet) { }
}
