package com.kj.stackchan.workday;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
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

    @BeforeEach
    void clearData() {
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
}
