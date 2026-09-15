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
    @Autowired com.kj.stackchan.interaction.ProactiveTopicCooldownRepository topics;
    @Autowired com.kj.stackchan.role.CompanionRoleRepository roles;

    @Test
    void timelineCountsBeyondItsBoundAndOrdersSoonestFirstWithoutCrossDeviceRows() {
        Instant now = Instant.parse("2026-09-14T02:00:00Z");
        UUID device = devices.saveAndFlush(new DeviceEntity("timeline", "test")).getId();
        UUID other = devices.saveAndFlush(new DeviceEntity("timeline-other", "test")).getId();
        UUID role = CompanionRoleEntity.DEFAULT_ROLE_ID;
        for (int i = 12; i >= 1; i--) {
            save(device, "pending-" + i, ReminderSource.USER, ReminderStatus.PENDING, now.plusSeconds(i * 60));
        }
        save(other, "other", ReminderSource.USER, ReminderStatus.PENDING, now);
        save(device, "heard", ReminderSource.PROACTIVE, ReminderStatus.DELIVERED, now.minusSeconds(20));
        var service = new ReminderService(reminders, devices, java.time.Clock.fixed(now, java.time.ZoneOffset.UTC),
                new ReminderScheduleCalculator(), roles);
        var timeline = service.timeline(device, role);
        assertThat(timeline.upcomingTotal()).isEqualTo(12);
        assertThat(timeline.upcoming()).hasSize(10);
        assertThat(timeline.upcoming().getFirst().content()).isEqualTo("pending-1");
        assertThat(timeline.upcoming().getLast().content()).isEqualTo("pending-10");
        assertThat(timeline.recent()).extracting(ReminderService.ReminderSnapshot::content).containsExactly("heard");
        assertThat(timeline.roleId()).isEqualTo(role);
    }

    @Test
    void recentDeliveryResolutionKeepsAllSourcesAndRecurringOccurrencesInScope() {
        Instant now = Instant.parse("2026-09-14T01:00:00Z");
        UUID device = devices.saveAndFlush(new DeviceEntity("recent-all-sources", "test")).getId();
        UUID other = devices.saveAndFlush(new DeviceEntity("recent-other", "test")).getId();
        UUID role = CompanionRoleEntity.DEFAULT_ROLE_ID;
        save(device, "old", ReminderSource.USER, ReminderStatus.DELIVERED, now.minusSeconds(1801));
        save(device, "future", ReminderSource.USER, ReminderStatus.DELIVERED, now.plusSeconds(1));
        save(device, "failed", ReminderSource.USER, ReminderStatus.FAILED, now);
        save(other, "other", ReminderSource.USER, ReminderStatus.DELIVERED, now);
        save(device, "proactive", ReminderSource.PROACTIVE, ReminderStatus.DELIVERED, now.minusSeconds(20));
        var recurring = new ReminderEntity(device, "recurring", now.minusSeconds(10), "UTC", now.minusSeconds(30));
        recurring.completeOccurrence(ReminderStatus.DELIVERED, now.plusSeconds(600), now.minusSeconds(10));
        reminders.saveAndFlush(recurring);
        assertThat(reminders.findRecentCompletedDeliveries(device, role, now.minusSeconds(1800), now, PageRequest.of(0, 2)))
                .extracting(ReminderEntity::getContent).containsExactly("recurring", "proactive");
        assertThat(reminders.findRecentCompletedDeliveries(device, role, now.minusSeconds(10), now, PageRequest.of(0, 2))).isEmpty();
        assertThat(reminders.findRecentCompletedDeliveries(device, UUID.randomUUID(), now.minusSeconds(1800), now, PageRequest.of(0, 2))).isEmpty();
    }

    @Test
    void feedbackTargetsTheHeardTopicRatherThanTheNewestQueuedTopic() {
        Instant now = Instant.parse("2026-09-13T01:00:00Z");
        UUID device = devices.saveAndFlush(new DeviceEntity("heard-topic", "test")).getId();
        UUID role = CompanionRoleEntity.DEFAULT_ROLE_ID;
        var service = new com.kj.stackchan.interaction.ProactiveTopicCooldownService(topics,
                java.time.Clock.fixed(now, java.time.ZoneOffset.UTC), reminders);
        service.recordMention(device, role, "heard", now.minusSeconds(50));
        service.recordMention(device, role, "queued", now.minusSeconds(1));
        var heard = new ReminderEntity(role, device, "已经听到的兴趣话题", now.minusSeconds(50), "UTC",
                ReminderRecurrence.NONE, 1, null, ReminderSource.PROACTIVE, "heard", null, now.minusSeconds(50));
        heard.completeOccurrence(ReminderStatus.DELIVERED, null, now.minusSeconds(40));
        reminders.saveAndFlush(heard);
        reminders.saveAndFlush(new ReminderEntity(role, device, "还没有播出的兴趣话题", now, "UTC",
                ReminderRecurrence.NONE, 1, null, ReminderSource.PROACTIVE, "queued", null, now));
        assertThat(service.muteLastDeliveredTopic(device, role, now.minusSeconds(40))).isFalse();
        assertThat(service.muteLastDeliveredTopic(device, role, null)).isTrue();
        assertThat(topics.findById(new com.kj.stackchan.interaction.ProactiveTopicCooldownId(device, role, "heard")))
                .get().satisfies(topic -> assertThat(topic.isUserMuted()).isTrue());
        assertThat(topics.findById(new com.kj.stackchan.interaction.ProactiveTopicCooldownId(device, role, "queued")))
                .get().satisfies(topic -> assertThat(topic.isUserMuted()).isFalse());
        // A newer generic greeting must not silently fall back to the earlier interest topic.
        save(device, "无主题问候", ReminderSource.PROACTIVE, ReminderStatus.DELIVERED, now.minusSeconds(1));
        assertThat(service.muteLastDeliveredTopic(device, role, null)).isFalse();
        save(device, "后来播出的普通提醒", ReminderSource.USER, ReminderStatus.DELIVERED, now);
        assertThat(service.muteLastDeliveredTopic(device, role, null)).isFalse();
        var context = new com.kj.stackchan.speech.RecentProactiveContextService(reminders,
                java.time.Clock.fixed(now, java.time.ZoneOffset.UTC), new com.fasterxml.jackson.databind.ObjectMapper());
        assertThat(context.context(device, role)).isEmpty();
    }

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
