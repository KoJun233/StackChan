package com.kj.stackchan.expression;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class ExpressionPackServiceTest {

    @Test
    void reconnectOnlyInstallsLifecyclePackAfterDeviceReportsCapability() {
        UUID deviceId = UUID.randomUUID();
        UUID packId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-23T00:00:00Z");
        ExpressionPackRepository packs = mock(ExpressionPackRepository.class);
        DeviceExpressionPackRepository mappings = mock(DeviceExpressionPackRepository.class);
        DeviceRepository devices = mock(DeviceRepository.class);
        DeviceCommandGateway gateway = mock(DeviceCommandGateway.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        DeviceExpressionPackEntity mapping = new DeviceExpressionPackEntity(deviceId, now);
        mapping.enable(packId, now);
        ExpressionPackEntity pack = mock(ExpressionPackEntity.class);
        when(pack.getId()).thenReturn(packId);
        when(pack.getPackType()).thenReturn(ExpressionPackType.LIFECYCLE_EAF);
        when(pack.getArtifactSha256()).thenReturn("a".repeat(64));
        when(pack.getArtifactSize()).thenReturn(1024);
        DeviceEntity device = mock(DeviceEntity.class);
        when(mappings.findById(deviceId)).thenReturn(Optional.of(mapping));
        when(packs.findById(packId)).thenReturn(Optional.of(pack));
        when(devices.findById(deviceId)).thenReturn(Optional.of(device));
        ExpressionPackService service = new ExpressionPackService(
                packs,
                mock(ExpressionPackStateRepository.class),
                mock(ExpressionPackClipRepository.class),
                mappings,
                devices,
                mock(ExpressionPackCompiler.class),
                mock(LifecycleExpressionPackCompiler.class),
                gateway,
                Clock.fixed(now, ZoneOffset.UTC),
                transactions);

        service.syncConnectedDevice(deviceId);

        verifyNoInteractions(gateway);

        when(device.isLifecycleClipSupported()).thenReturn(true);
        when(gateway.installExpressionPack(
                eq(deviceId), eq(packId), eq("a".repeat(64)), eq(1024), anyString()))
                .thenReturn(true);

        service.syncConnectedDevice(deviceId);

        verify(gateway).installExpressionPack(
                eq(deviceId), eq(packId), eq("a".repeat(64)), eq(1024), anyString());
    }
}
