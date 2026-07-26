package com.kj.stackchan.memory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LongTermMemoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");

    @Mock
    private LongTermMemoryRepository repository;
    @Mock
    private DeviceRepository deviceRepository;

    @Test
    void userEnteredMemoryIsImmediatelyConfirmedAndEnabled() {
        when(repository.save(any(LongTermMemoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LongTermMemoryService.MemorySnapshot result = service().create(new LongTermMemoryService.MemoryCommand(
                MemoryScopeType.GLOBAL,
                null,
                MemoryCategory.USER_PROFILE,
                "称呼偏好",
                "用户喜欢被称为阿俊"
        ));

        assertThat(result.source()).isEqualTo(MemorySource.USER_ENTERED);
        assertThat(result.confirmationStatus()).isEqualTo(MemoryConfirmationStatus.CONFIRMED);
        assertThat(result.enabled()).isTrue();
        assertThat(result.confirmedAt()).isEqualTo(NOW);
    }

    @Test
    void assistantSuggestionRemainsDisabledUntilExplicitConfirmation() {
        when(repository.save(any(LongTermMemoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LongTermMemoryService service = service();
        LongTermMemoryService.MemorySnapshot suggestion = service.suggest(
                new LongTermMemoryService.MemorySuggestionCommand(
                        new LongTermMemoryService.MemoryCommand(
                                MemoryScopeType.GLOBAL,
                                null,
                                MemoryCategory.EVENT,
                                "项目里程碑",
                                "用户完成了第一轮设备联调"
                        ),
                        "机器人根据本轮对话提出，等待用户确认"
                )
        );

        assertThat(suggestion.confirmationStatus()).isEqualTo(MemoryConfirmationStatus.PENDING);
        assertThat(suggestion.enabled()).isFalse();

        LongTermMemoryEntity stored = entityFrom(suggestion);
        when(repository.findById(suggestion.id())).thenReturn(Optional.of(stored));
        LongTermMemoryService.MemorySnapshot confirmed = service.confirm(suggestion.id());

        assertThat(confirmed.confirmationStatus()).isEqualTo(MemoryConfirmationStatus.CONFIRMED);
        assertThat(confirmed.enabled()).isTrue();
    }

    @Test
    void rejectsUnknownDeviceScopeAndCannotEnablePendingMemory() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepository.existsById(deviceId)).thenReturn(false);

        assertThatThrownBy(() -> service().create(new LongTermMemoryService.MemoryCommand(
                MemoryScopeType.DEVICE,
                deviceId,
                MemoryCategory.EVENT,
                "设备偏好",
                "只在这台设备上使用"
        ))).isInstanceOf(InvalidMemoryException.class);

        LongTermMemoryEntity pending = new LongTermMemoryEntity(
                MemoryScopeType.GLOBAL,
                null,
                MemoryCategory.EVENT,
                "待确认",
                "尚未确认",
                MemorySource.ASSISTANT_SUGGESTED,
                "等待确认",
                MemoryConfirmationStatus.PENDING,
                NOW
        );
        when(repository.findById(pending.getId())).thenReturn(Optional.of(pending));
        assertThatThrownBy(() -> service().setEnabled(pending.getId(), true))
                .isInstanceOf(InvalidMemoryException.class);
    }

    private LongTermMemoryEntity entityFrom(LongTermMemoryService.MemorySnapshot snapshot) {
        return new LongTermMemoryEntity(
                snapshot.scopeType(),
                snapshot.deviceId(),
                snapshot.category(),
                snapshot.title(),
                snapshot.content(),
                snapshot.source(),
                snapshot.sourceDetail(),
                snapshot.confirmationStatus(),
                snapshot.createdAt()
        );
    }

    private LongTermMemoryService service() {
        return new LongTermMemoryService(
                repository,
                deviceRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
