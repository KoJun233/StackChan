package com.kj.stackchan.wakeword;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WakeWordModelJobServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");
    private static final UUID DEVICE_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String MODEL_NAME = "wn9_xiao3feng1xiao3feng1_tts3";
    private static final String SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock
    private WakeWordModelJobRepository jobRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private EspSrWakeWordModelCatalog catalog;
    @Mock
    private DeviceCommandGateway commandGateway;
    @Mock
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void configureTransactions() {
        lenient().when(transactionManager.getTransaction(any()))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
    }

    @Test
    void createsAReadyJobFromTheTrustedCatalog() {
        byte[] artifact = {1, 2, 3};
        when(deviceRepository.existsById(DEVICE_ID)).thenReturn(true);
        when(jobRepository.existsByDeviceIdAndStatusIn(eq(DEVICE_ID), any())).thenReturn(false);
        when(jobRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalog.requireOption(MODEL_NAME))
                .thenReturn(new WakeWordModelOption(MODEL_NAME, "小峰小峰", "zh-CN"));
        when(catalog.packageModel(MODEL_NAME))
                .thenReturn(new GeneratedWakeWordModel(MODEL_NAME, SHA256, artifact));

        WakeWordModelJobEntity job = service().create(DEVICE_ID, MODEL_NAME);

        assertThat(job.getPhrase()).isEqualTo("小峰小峰");
        assertThat(job.getModelName()).isEqualTo(MODEL_NAME);
        assertThat(job.getStatus()).isEqualTo(WakeWordModelJobStatus.READY);
        assertThat(job.getArtifact()).containsExactly(artifact);
    }

    @Test
    void dispatchesAValidatedArtifactAndCompletesAfterDeviceHealthStatus() {
        byte[] artifact = {1, 2, 3};
        WakeWordModelJobEntity job = new WakeWordModelJobEntity(
                DEVICE_ID, "小峰小峰", new GeneratedWakeWordModel(MODEL_NAME, SHA256, artifact), NOW);
        when(jobRepository.findTop10ByStatusOrderByCreatedAtAsc(WakeWordModelJobStatus.READY))
                .thenReturn(List.of(job));
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        when(commandGateway.isConnected(DEVICE_ID)).thenReturn(true);
        when(commandGateway.installWakeModel(
                eq(DEVICE_ID), eq(job.getId()), eq(MODEL_NAME), eq(SHA256), eq(artifact.length), anyString()))
                .thenReturn(true);

        WakeWordModelJobService service = service();
        service.dispatchReadyJobs();

        assertThat(job.getStatus()).isEqualTo(WakeWordModelJobStatus.INSTALLING);
        when(jobRepository.findByCommandId(job.getCommandId())).thenReturn(Optional.of(job));
        assertThat(service.recordCommandAcknowledgement(DEVICE_ID, job.getCommandId(), true)).isTrue();

        when(jobRepository.findByIdAndDeviceId(job.getId(), DEVICE_ID)).thenReturn(Optional.of(job));
        service.record(DEVICE_ID, job.getId(), "INSTALLED", MODEL_NAME, SHA256);

        assertThat(job.getStatus()).isEqualTo(WakeWordModelJobStatus.INSTALLED);
        assertThat(job.getArtifact()).isNull();
        verify(commandGateway).installWakeModel(
                eq(DEVICE_ID), eq(job.getId()), eq(MODEL_NAME), eq(SHA256), eq(artifact.length), anyString());
    }

    private WakeWordModelJobService service() {
        return new WakeWordModelJobService(
                jobRepository,
                deviceRepository,
                catalog,
                commandGateway,
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager
        );
    }
}
