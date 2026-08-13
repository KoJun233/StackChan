package com.kj.stackchan.memory;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.role.CompanionRoleEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LongTermMemoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");

    @Mock
    private LongTermMemoryRepository repository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private MemoryUsageRepository usageRepository;

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
        when(repository.findByIdForUpdate(suggestion.id())).thenReturn(Optional.of(stored));
        LongTermMemoryService.MemorySnapshot confirmed = service.confirm(suggestion.id());

        assertThat(confirmed.confirmationStatus()).isEqualTo(MemoryConfirmationStatus.CONFIRMED);
        assertThat(confirmed.enabled()).isTrue();
    }

    @Test
    void conflictingSuggestionOnlySupersedesOldMemoryAfterConfirmation() {
        LongTermMemoryEntity oldMemory = new LongTermMemoryEntity(
                MemoryScopeType.GLOBAL, null, MemoryCategory.USER_PROFILE,
                "称呼偏好", "称呼用户为阿俊", MemorySource.USER_ENTERED, LongTermMemoryService.USER_ENTERED_DETAIL,
                MemoryConfirmationStatus.CONFIRMED, "称呼偏好", 4, null, null, false, NOW
        );
        when(repository.findActiveTopicMatches(
                CompanionRoleEntity.DEFAULT_ROLE_ID, "称呼偏好", MemoryScopeType.GLOBAL, null))
                .thenReturn(List.of(oldMemory));
        when(repository.save(any(LongTermMemoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        LongTermMemoryService service = service();

        LongTermMemoryService.MemorySnapshot suggestion = service.suggest(
                new LongTermMemoryService.MemorySuggestionCommand(
                        new LongTermMemoryService.MemoryCommand(
                                MemoryScopeType.GLOBAL, null, MemoryCategory.USER_PROFILE,
                                "称呼偏好", "称呼用户为小俊", "称呼偏好", 4, false
                        ),
                        "用户在本轮明确更新称呼",
                        UUID.randomUUID()
                )
        );

        assertThat(suggestion.replacesMemoryId()).isEqualTo(oldMemory.getId());
        assertThat(oldMemory.isEnabled()).isTrue();

        LongTermMemoryEntity pending = entityFrom(suggestion);
        pending.setReplacementCandidate(oldMemory.getId(), NOW);
        when(repository.findByIdForUpdate(pending.getId())).thenReturn(Optional.of(pending));
        when(repository.findByIdForUpdate(oldMemory.getId())).thenReturn(Optional.of(oldMemory));

        service.confirm(pending.getId());

        assertThat(oldMemory.isEnabled()).isFalse();
        assertThat(oldMemory.getSupersededByMemoryId()).isEqualTo(pending.getId());
        when(repository.findById(oldMemory.getId())).thenReturn(Optional.of(oldMemory));
        assertThatThrownBy(() -> service.setEnabled(oldMemory.getId(), true))
                .isInstanceOf(InvalidMemoryException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToOrdinaryIndexWhenTrigramQueryIsUnavailable() {
        LongTermMemoryEntity memory = new LongTermMemoryEntity(
                MemoryScopeType.GLOBAL, null, MemoryCategory.EVENT,
                "项目进度", "完成联调", MemorySource.USER_ENTERED, LongTermMemoryService.USER_ENTERED_DETAIL,
                MemoryConfirmationStatus.CONFIRMED, "项目进度", 5, null, null, false, NOW
        );
        when(repository.searchContext(CompanionRoleEntity.DEFAULT_ROLE_ID, null, "项目", 8))
                .thenThrow(new DataAccessResourceFailureException("pg_trgm unavailable"));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(memory)));

        List<LongTermMemoryService.MemorySnapshot> result = service().loadContext(null, "项目", 20);

        assertThat(result).extracting(LongTermMemoryService.MemorySnapshot::id).containsExactly(memory.getId());
    }

    @Test
    void recordsOnlyMemoryIdsIdempotentlyForACompletedTurn() {
        UUID turnId = UUID.randomUUID();
        LongTermMemoryEntity memory = new LongTermMemoryEntity(
                MemoryScopeType.GLOBAL, null, MemoryCategory.EVENT,
                "项目进度", "完成联调", MemorySource.USER_ENTERED, LongTermMemoryService.USER_ENTERED_DETAIL,
                MemoryConfirmationStatus.CONFIRMED, "项目进度", 3, null, null, false, NOW
        );
        when(repository.findAllById(any())).thenReturn(List.of(memory));
        when(usageRepository.existsById(any(MemoryUsageId.class))).thenReturn(false, true);
        LongTermMemoryService service = serviceWithUsage();

        service.recordUsage(turnId, List.of(memory.getId(), memory.getId()));
        service.recordUsage(turnId, List.of(memory.getId()));

        verify(usageRepository).save(any(MemoryUsageEntity.class));
        assertThat(memory.getLastUsedAt()).isEqualTo(NOW);
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
                snapshot.topicKey(),
                snapshot.importance(),
                snapshot.sourceTurnId(),
                snapshot.replacesMemoryId(),
                snapshot.allowProactiveMention(),
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

    private LongTermMemoryService serviceWithUsage() {
        return new LongTermMemoryService(
                repository,
                deviceRepository,
                usageRepository,
                new MemorySuggestionSafetyPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
