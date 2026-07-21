package com.kj.stackchan.device;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Import(PairingServiceTest.FixedClockConfiguration.class)
class PairingServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");

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

    @Autowired
    private PairingService pairingService;

    @Autowired
    private PairingCodeRepository pairingCodeRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Autowired
    private DeviceConnectionRegistry connectionRegistry;

    @Test
    void claimsEachPairingCodeOnlyOnce() {
        PairingCodeEntity pairingCode = pairingService.createCode("test-operator");

        Optional<PairingService.PairingClaim> firstClaim = pairingService.claim(
                pairingCode.getValue(),
                "stackchan-001",
                "1.2.3"
        );
        Optional<PairingService.PairingClaim> secondClaim = pairingService.claim(
                pairingCode.getValue(),
                "stackchan-002",
                "1.2.3"
        );

        assertThat(firstClaim)
                .isPresent()
                .get()
                .extracting(claim -> claim.device().getHardwareId())
                .isEqualTo("stackchan-001");
        assertThat(secondClaim).isEmpty();
    }

    @Test
    void generatesATwelveCharacterCodeThatCanBeClaimed() {
        PairingCodeEntity pairingCode = pairingService.createCode("test-operator");

        assertThat(pairingCode.getValue()).hasSize(12);

        Optional<PairingService.PairingClaim> claim = pairingService.claim(
                pairingCode.getValue(),
                "stackchan-generated-code-001",
                "1.2.3"
        );

        assertThat(claim).isPresent();
    }

    @Test
    void repairingTheSameHardwarePreservesTheDeviceAndRotatesCredentials() {
        PairingService.PairingClaim first = pairingService.claim(
                pairingService.createCode("operator").getValue(),
                "stackchan-repair-001",
                "1.0.0"
        ).orElseThrow();
        String firstRefreshTokenHash = first.device().getRefreshTokenHash();
        first.device().recordHeartbeat(FIXED_NOW, "active", null);
        deviceRepository.saveAndFlush(first.device());

        assertThat(deviceRepository.findById(first.device().getId()).orElseThrow().getSafetyState())
                .isEqualTo("active");

        PairingService.PairingClaim second = pairingService.claim(
                pairingService.createCode("operator").getValue(),
                "stackchan-repair-001",
                "1.1.0"
        ).orElseThrow();

        assertThat(second.device().getId()).isEqualTo(first.device().getId());
        assertThat(second.device().getCredentialVersion()).isGreaterThan(first.credentialVersionBeforeRotation());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.device().getRefreshTokenHash()).isNotEqualTo(firstRefreshTokenHash);
        assertThat(second.device().getFirmwareVersion()).isEqualTo("1.1.0");
        assertThat(second.device().getSafetyState()).isEqualTo("motion_disabled");
    }

    @Test
    void repairingTheSameHardwareRevokesItsActiveSocketAfterCommit() throws Exception {
        PairingService.PairingClaim first = pairingService.claim(
                pairingService.createCode("operator").getValue(),
                "stackchan-active-repair-001",
                "1.0.0"
        ).orElseThrow();
        WebSocketSession activeSession = authenticatedSession(
                first.device().getId(),
                first.device().getCredentialVersion(),
                FIXED_NOW.plusSeconds(3600)
        );
        connectionRegistry.register(first.device().getId(), activeSession);

        PairingService.PairingClaim second = pairingService.claim(
                pairingService.createCode("operator").getValue(),
                "stackchan-active-repair-001",
                "1.1.0"
        ).orElseThrow();

        assertThat(second.device().getCredentialVersion()).isGreaterThan(first.device().getCredentialVersion());
        verify(activeSession).close(CloseStatus.POLICY_VIOLATION);
        assertThat(connectionRegistry.sessionStateCount()).isZero();
    }

    @Test
    void serializesConcurrentCredentialRotationForTheSameExistingHardware() throws Exception {
        DeviceEntity unsavedDevice = new DeviceEntity("stackchan-concurrent-repair-001", "1.0.0");
        unsavedDevice.rotateCredentials("a".repeat(64), FIXED_NOW.minusSeconds(1));
        DeviceEntity existingDevice = deviceRepository.saveAndFlush(unsavedDevice);
        UUID deviceId = existingDevice.getId();
        long startingVersion = existingDevice.getCredentialVersion();
        PairingCodeEntity firstCode = pairingService.createCode("operator-one");
        PairingCodeEntity secondCode = pairingService.createCode("operator-two");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<PairingService.PairingClaim> firstClaim = executor.submit(() -> claimAfterStart(
                    ready,
                    start,
                    firstCode.getValue(),
                    existingDevice.getHardwareId()
            ).orElseThrow());
            Future<PairingService.PairingClaim> secondClaim = executor.submit(() -> claimAfterStart(
                    ready,
                    start,
                    secondCode.getValue(),
                    existingDevice.getHardwareId()
            ).orElseThrow());

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<PairingService.PairingClaim> claims = List.of(
                    firstClaim.get(5, TimeUnit.SECONDS),
                    secondClaim.get(5, TimeUnit.SECONDS)
            );
            List<Long> credentialVersions = claims.stream()
                    .map(claim -> claim.device().getCredentialVersion())
                    .sorted()
                    .toList();
            PairingService.PairingClaim lowerVersionClaim = claims.stream()
                    .min((left, right) -> Long.compare(
                            left.device().getCredentialVersion(),
                            right.device().getCredentialVersion()
                    ))
                    .orElseThrow();
            PairingService.PairingClaim higherVersionClaim = claims.stream()
                    .max((left, right) -> Long.compare(
                            left.device().getCredentialVersion(),
                            right.device().getCredentialVersion()
                    ))
                    .orElseThrow();
            String lowerVersionToken = deviceTokenService.issue(lowerVersionClaim.device()).value();
            String higherVersionToken = deviceTokenService.issue(higherVersionClaim.device()).value();
            DeviceEntity finalDevice = deviceRepository.findById(deviceId).orElseThrow();

            assertThat(credentialVersions).containsExactly(startingVersion + 1, startingVersion + 2);
            assertThat(finalDevice.getCredentialVersion()).isEqualTo(startingVersion + 2);
            assertThatThrownBy(() -> deviceTokenService.verify(lowerVersionToken))
                    .isInstanceOf(InvalidDeviceTokenException.class);
            assertThat(deviceTokenService.verify(higherVersionToken).deviceId()).isEqualTo(deviceId);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void allowsOnlyOneConcurrentClaim() throws Exception {
        PairingCodeEntity pairingCode = pairingService.createCode("test-operator");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<PairingService.PairingClaim>> firstClaim = executor.submit(() -> claimAfterStart(
                    ready,
                    start,
                    pairingCode.getValue(),
                    "stackchan-concurrent-001"
            ));
            Future<Optional<PairingService.PairingClaim>> secondClaim = executor.submit(() -> claimAfterStart(
                    ready,
                    start,
                    pairingCode.getValue(),
                    "stackchan-concurrent-002"
            ));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Optional<PairingService.PairingClaim>> claims = List.of(
                    firstClaim.get(5, TimeUnit.SECONDS),
                    secondClaim.get(5, TimeUnit.SECONDS)
            );
            assertThat(claims.stream().filter(Optional::isPresent).count()).isEqualTo(1);
            assertThat(claims.stream().filter(Optional::isEmpty).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsCodeThatExpiresAtCurrentTime() {
        PairingCodeEntity expiredCode = pairingCodeRepository.save(new PairingCodeEntity(
                "expired-" + UUID.randomUUID(),
                "test-operator",
                FIXED_NOW,
                null
        ));

        Optional<PairingService.PairingClaim> claim = pairingService.claim(
                expiredCode.getValue(),
                "stackchan-expired-001",
                "1.2.3"
        );

        assertThat(claim).isEmpty();
    }

    private Optional<PairingService.PairingClaim> claimAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            String codeValue,
            String hardwareId
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent claim start was not released");
        }
        return pairingService.claim(codeValue, hardwareId, "1.2.3");
    }

    private WebSocketSession authenticatedSession(UUID deviceId, long credentialVersion, Instant expiresAt) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(DeviceWebSocketHandshakeInterceptor.DEVICE_ID_ATTRIBUTE, deviceId);
        attributes.put(
                DeviceWebSocketHandshakeInterceptor.class.getName() + ".credentialVersion",
                credentialVersion
        );
        attributes.put(
                DeviceWebSocketHandshakeInterceptor.class.getName() + ".tokenExpiresAt",
                expiresAt
        );
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}
