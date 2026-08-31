package com.kj.stackchan.workday;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.task.PersonalTaskEntity;
import com.kj.stackchan.task.PersonalTaskPriority;
import com.kj.stackchan.task.PersonalTaskRepository;
import com.kj.stackchan.task.PersonalTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class WorkdayRuntimePersistenceTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private DeviceRepository deviceRepository;
    @Autowired private DeviceWorkdaySettingsRepository settingsRepository;
    @Autowired private DeviceWorkdayRuntimeRepository runtimeRepository;
    @Autowired private WorkdayBriefAttemptRepository briefRepository;
    @Autowired private WorkdayDailyMetricRepository metricRepository;
    @Autowired private WorkdayPilotObservationRepository observationRepository;
    @Autowired private PersonalTaskRepository personalTaskRepository;

    @BeforeEach
    void clearData() {
        personalTaskRepository.deleteAllInBatch();
        observationRepository.deleteAllInBatch();
        metricRepository.deleteAllInBatch();
        briefRepository.deleteAllInBatch();
        runtimeRepository.deleteAllInBatch();
        settingsRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
    }

    @Test
    @Transactional
    void persistsRuntimeAndAtomicallyClaimsOneBriefPerWorkday() {
        DeviceEntity device = deviceRepository.save(new DeviceEntity("workday-runtime", "1.0.0"));
        Instant now = Instant.parse("2026-08-24T01:00:00Z");
        LocalDate workDate = LocalDate.of(2026, 8, 24);
        DeviceWorkdayRuntimeEntity runtime = new DeviceWorkdayRuntimeEntity(device.getId(), now);
        runtime.start(workDate, true, now);
        runtimeRepository.save(runtime);

        int first = briefRepository.claim(UUID.randomUUID(), device.getId(), workDate, now);
        int replay = briefRepository.claim(UUID.randomUUID(), device.getId(), workDate, now.plusSeconds(1));

        assertThat(runtimeRepository.findById(device.getId()))
                .get()
                .extracting(DeviceWorkdayRuntimeEntity::getState)
                .isEqualTo(WorkdayRuntimeState.ACTIVE_PRESENT);
        assertThat(first).isEqualTo(1);
        assertThat(replay).isZero();
        assertThat(briefRepository.findByDeviceIdAndWorkDate(device.getId(), workDate))
                .get()
                .extracting(WorkdayBriefAttemptEntity::getStatus)
                .isEqualTo(WorkdayBriefStatus.PENDING);
    }

    @Test
    @Transactional
    void persistsRoleScopedPersonalTaskLifecycle() {
        DeviceEntity device = deviceRepository.save(new DeviceEntity("personal-task", "1.0.0"));
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        PersonalTaskEntity task = personalTaskRepository.save(new PersonalTaskEntity(
                device.getId(), CompanionRoleEntity.DEFAULT_ROLE_ID, "整理会议材料", "周一使用",
                PersonalTaskPriority.HIGH, Instant.parse("2026-08-31T01:00:00Z"), "Asia/Shanghai", now));

        task.complete(now.plusSeconds(60));
        personalTaskRepository.flush();

        assertThat(personalTaskRepository.findById(task.getId()))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getStatus()).isEqualTo(PersonalTaskStatus.COMPLETED);
                    assertThat(saved.getCompletedAt()).isEqualTo(now.plusSeconds(60));
                    assertThat(saved.getRoleId()).isEqualTo(CompanionRoleEntity.DEFAULT_ROLE_ID);
                });
    }

    @Test
    @Transactional
    void persistsAndResetsThePilotCompletionNotificationMarker() {
        DeviceEntity device = deviceRepository.save(new DeviceEntity("workday-pilot", "1.0.0"));
        Instant startedAt = Instant.parse("2026-08-30T09:35:33Z");
        Instant queuedAt = Instant.parse("2026-09-13T00:05:00Z");
        WorkdayPilotObservationEntity observation = observationRepository.save(
                new WorkdayPilotObservationEntity(
                        device.getId(), LocalDate.of(2026, 8, 30), "Asia/Shanghai", 31, startedAt
                )
        );

        observation.markCompletionNotificationQueued(queuedAt);
        observationRepository.flush();

        assertThat(observationRepository.findById(device.getId()))
                .get()
                .extracting(WorkdayPilotObservationEntity::getCompletionNotificationQueuedAt)
                .isEqualTo(queuedAt);
        assertThat(observationRepository
                .findAllByCompletionNotificationQueuedAtIsNullOrderByEndsOnAsc()).isEmpty();

        observation.reset(LocalDate.of(2026, 9, 14), "Asia/Shanghai", 31, queuedAt.plusSeconds(60));
        observationRepository.flush();

        assertThat(observationRepository
                .findAllByCompletionNotificationQueuedAtIsNullOrderByEndsOnAsc())
                .extracting(WorkdayPilotObservationEntity::getDeviceId)
                .containsExactly(device.getId());
    }
}
