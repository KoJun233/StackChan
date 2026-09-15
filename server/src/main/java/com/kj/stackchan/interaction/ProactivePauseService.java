package com.kj.stackchan.interaction;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.role.CompanionRoleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProactivePauseService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final InteractionSettingsService settings;
    private final DeviceRepository devices;
    private final CompanionRoleRepository roles;

    public ProactivePauseService(JdbcTemplate jdbc, Clock clock, InteractionSettingsService settings,
            DeviceRepository devices, CompanionRoleRepository roles) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
        this.devices = devices;
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public boolean isPaused(UUID deviceId, UUID roleId, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from role_proactive_pauses
                where device_id = ? and role_id = ? and paused_until > ?)
                """, Boolean.class, deviceId, roleId, Timestamp.from(now)));
    }

    @Transactional(readOnly = true)
    public PauseSnapshot get(UUID deviceId, UUID roleId) {
        validate(deviceId, roleId);
        var rows = jdbc.query("select paused_until from role_proactive_pauses where device_id = ? and role_id = ?",
                (row, index) -> row.getTimestamp(1).toInstant(), deviceId, roleId);
        Instant until = rows.isEmpty() ? null : rows.getFirst();
        return new PauseSnapshot(deviceId, roleId, until, until != null && until.isAfter(clock.instant()));
    }

    @Transactional
    public PauseSnapshot pause(UUID deviceId, UUID roleId, Integer minutes) {
        validate(deviceId, roleId);
        Instant now = clock.instant();
        Instant until;
        if (minutes == null) {
            ZoneId zone = ZoneId.of(settings.resolve(deviceId).zoneId());
            until = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
        } else {
            if (minutes < 1 || minutes > 1440) throw new InvalidInteractionSettingsException("Pause duration is invalid");
            until = now.plus(Duration.ofMinutes(minutes));
        }
        jdbc.update("""
                insert into role_proactive_pauses(device_id, role_id, paused_until, updated_at) values (?, ?, ?, ?)
                on conflict (device_id, role_id) do update
                set paused_until = excluded.paused_until, updated_at = excluded.updated_at
                """, deviceId, roleId, Timestamp.from(until), Timestamp.from(now));
        return new PauseSnapshot(deviceId, roleId, until, true);
    }

    @Transactional
    public PauseSnapshot resume(UUID deviceId, UUID roleId) {
        validate(deviceId, roleId);
        jdbc.update("delete from role_proactive_pauses where device_id = ? and role_id = ?", deviceId, roleId);
        return new PauseSnapshot(deviceId, roleId, null, false);
    }

    private void validate(UUID deviceId, UUID roleId) {
        if (deviceId == null || roleId == null || !devices.existsById(deviceId) || !roles.existsById(roleId)) {
            throw new InvalidInteractionSettingsException("Pause device or role is invalid");
        }
    }

    public record PauseSnapshot(UUID deviceId, UUID roleId, Instant pausedUntil, boolean paused) { }
}
