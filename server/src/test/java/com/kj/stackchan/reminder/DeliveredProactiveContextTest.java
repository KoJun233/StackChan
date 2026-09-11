package com.kj.stackchan.reminder;

import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.role.CompanionRoleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DeliveredProactiveContextTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired ReminderRepository reminders;
    @Autowired DeviceRepository devices;
    @Autowired JdbcTemplate jdbc;

    @Test
    void returnsOnlyLatestSuccessfulProactiveMessageInTheDeviceRoleAndTimeWindow() {
        Instant now = Instant.parse("2026-09-12T00:00:00Z");
        UUID device = devices.saveAndFlush(new DeviceEntity("proactive-context", "test")).getId();
        UUID otherDevice = devices.saveAndFlush(new DeviceEntity("other-context", "test")).getId();
        UUID role = CompanionRoleEntity.DEFAULT_ROLE_ID;
        save(device, "old", ReminderSource.PROACTIVE, ReminderStatus.DELIVERED, now.minusSeconds(1801));
        save(device, "first", ReminderSource.PROACTIVE, ReminderStatus.DELIVERED, now.minusSeconds(100));
        save(device, "latest", ReminderSource.PROACTIVE, ReminderStatus.DELIVERED, now.minusSeconds(50));
        save(device, "future", ReminderSource.PROACTIVE, ReminderStatus.DELIVERED, now.plusSeconds(1));
        save(device, "user reminder", ReminderSource.USER, ReminderStatus.DELIVERED, now);
        save(otherDevice, "other device", ReminderSource.PROACTIVE, ReminderStatus.DELIVERED, now);
        for (var status : new ReminderStatus[]{ReminderStatus.FAILED, ReminderStatus.CANCELLED,
                ReminderStatus.PENDING, ReminderStatus.DISPATCHED, ReminderStatus.EXPIRED}) {
            save(device, status.name(), ReminderSource.PROACTIVE, status, now.minusSeconds(1));
        }
        var result = reminders.findDeliveredProactiveContext(device, role, now.minusSeconds(1800), now, PageRequest.of(0, 1));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getContent()).isEqualTo("latest");
        assertThat(result.getFirst().getCompletedAt()).isEqualTo(now.minusSeconds(50));
        assertThat(reminders.findDeliveredProactiveContext(device, UUID.randomUUID(),
                now.minusSeconds(1800), now, PageRequest.of(0, 1))).isEmpty();
        jdbc.update("delete from reminders where content = 'latest'");
        assertThat(reminders.findDeliveredProactiveContext(device, role,
                now.minusSeconds(1800), now, PageRequest.of(0, 1)).getFirst().getContent()).isEqualTo("first");
    }

    private void save(UUID deviceId, String content, ReminderSource source, ReminderStatus status, Instant completedAt) {
        var reminder = new ReminderEntity(deviceId, content, completedAt, "UTC",
                ReminderRecurrence.NONE, 1, null, source, completedAt);
        if (status == ReminderStatus.DISPATCHED) {
            reminder.markDispatched(UUID.randomUUID().toString(), new byte[44], completedAt);
        } else if (status != ReminderStatus.PENDING) {
            reminder.completeOccurrence(status, null, completedAt);
        }
        reminders.saveAndFlush(reminder);
    }
}
