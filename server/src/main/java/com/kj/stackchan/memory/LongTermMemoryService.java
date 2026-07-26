package com.kj.stackchan.memory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LongTermMemoryService {

    public static final String USER_ENTERED_DETAIL = "由管理员在控制台明确添加";

    private final LongTermMemoryRepository repository;
    private final DeviceRepository deviceRepository;
    private final Clock clock;

    public LongTermMemoryService(
            LongTermMemoryRepository repository,
            DeviceRepository deviceRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.deviceRepository = deviceRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MemoryPage list(
            String query,
            MemoryCategory category,
            MemoryConfirmationStatus confirmationStatus,
            Boolean enabled,
            MemoryScopeType scopeType,
            UUID deviceId,
            int from,
            int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int page = Math.max(from, 0) / safeLimit;
        Specification<LongTermMemoryEntity> specification = baseSpecification(
                query, category, confirmationStatus, enabled, scopeType, deviceId
        );
        Page<LongTermMemoryEntity> result = repository.findAll(
                specification,
                PageRequest.of(page, safeLimit, Sort.by(Sort.Direction.DESC, "updatedAt", "id"))
        );
        return new MemoryPage(result.getContent().stream().map(this::toSnapshot).toList(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public MemorySnapshot get(UUID id) {
        return toSnapshot(find(id));
    }

    @Transactional
    public MemorySnapshot create(MemoryCommand command) {
        ValidatedMemory validated = validate(command);
        Instant now = clock.instant();
        LongTermMemoryEntity memory = new LongTermMemoryEntity(
                validated.scopeType(),
                validated.deviceId(),
                validated.category(),
                validated.title(),
                validated.content(),
                MemorySource.USER_ENTERED,
                USER_ENTERED_DETAIL,
                MemoryConfirmationStatus.CONFIRMED,
                now
        );
        return toSnapshot(repository.save(memory));
    }

    @Transactional
    public MemorySnapshot suggest(MemorySuggestionCommand command) {
        ValidatedMemory validated = validate(command.memory());
        String sourceDetail = normalize(command.sourceDetail(), 500, "Memory suggestion reason is invalid", false);
        Instant now = clock.instant();
        LongTermMemoryEntity memory = new LongTermMemoryEntity(
                validated.scopeType(),
                validated.deviceId(),
                validated.category(),
                validated.title(),
                validated.content(),
                MemorySource.ASSISTANT_SUGGESTED,
                sourceDetail,
                MemoryConfirmationStatus.PENDING,
                now
        );
        return toSnapshot(repository.save(memory));
    }

    @Transactional
    public MemorySnapshot update(UUID id, MemoryCommand command) {
        ValidatedMemory validated = validate(command);
        LongTermMemoryEntity memory = find(id);
        String sourceDetail = memory.getSource() == MemorySource.USER_ENTERED
                ? USER_ENTERED_DETAIL
                : memory.getSourceDetail();
        memory.update(
                validated.scopeType(),
                validated.deviceId(),
                validated.category(),
                validated.title(),
                validated.content(),
                sourceDetail,
                clock.instant()
        );
        return toSnapshot(memory);
    }

    @Transactional
    public MemorySnapshot confirm(UUID id) {
        LongTermMemoryEntity memory = find(id);
        memory.confirm(clock.instant());
        return toSnapshot(memory);
    }

    @Transactional
    public MemorySnapshot reject(UUID id) {
        LongTermMemoryEntity memory = find(id);
        memory.reject(clock.instant());
        return toSnapshot(memory);
    }

    @Transactional
    public MemorySnapshot setEnabled(UUID id, boolean enabled) {
        LongTermMemoryEntity memory = find(id);
        if (enabled && memory.getConfirmationStatus() != MemoryConfirmationStatus.CONFIRMED) {
            throw new InvalidMemoryException("Only confirmed memory can be enabled");
        }
        memory.setEnabled(enabled, clock.instant());
        return toSnapshot(memory);
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(find(id));
    }

    @Transactional
    public long clear(MemoryScopeType scopeType, UUID deviceId) {
        validateScopeFilter(scopeType, deviceId);
        List<LongTermMemoryEntity> memories = repository.findAll(baseSpecification(
                "", null, null, null, scopeType, deviceId
        ));
        repository.deleteAll(memories);
        return memories.size();
    }

    @Transactional(readOnly = true)
    public List<MemorySnapshot> loadContext(UUID deviceId, int limit) {
        Specification<LongTermMemoryEntity> specification = (root, query, builder) -> builder.and(
                builder.equal(root.get("confirmationStatus"), MemoryConfirmationStatus.CONFIRMED),
                builder.isTrue(root.get("enabled")),
                deviceId == null
                        ? builder.equal(root.get("scopeType"), MemoryScopeType.GLOBAL)
                        : builder.or(
                                builder.equal(root.get("scopeType"), MemoryScopeType.GLOBAL),
                                builder.and(
                                        builder.equal(root.get("scopeType"), MemoryScopeType.DEVICE),
                                        builder.equal(root.get("deviceId"), deviceId)
                                )
                        )
        );
        return repository.findAll(
                        specification,
                        PageRequest.of(0, Math.min(Math.max(limit, 1), 100),
                                Sort.by(Sort.Direction.DESC, "updatedAt", "id"))
                )
                .getContent()
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    private Specification<LongTermMemoryEntity> baseSpecification(
            String queryText,
            MemoryCategory category,
            MemoryConfirmationStatus confirmationStatus,
            Boolean enabled,
            MemoryScopeType scopeType,
            UUID deviceId
    ) {
        validateScopeFilter(scopeType, deviceId);
        Specification<LongTermMemoryEntity> specification = (root, query, builder) -> builder.conjunction();
        if (queryText != null && !queryText.isBlank()) {
            String pattern = "%" + queryText.trim().toLowerCase() + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("title")), pattern),
                    builder.like(builder.lower(root.get("content")), pattern)
            ));
        }
        if (category != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("category"), category));
        }
        if (confirmationStatus != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("confirmationStatus"), confirmationStatus));
        }
        if (enabled != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("enabled"), enabled));
        }
        if (scopeType != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("scopeType"), scopeType));
        }
        if (deviceId != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("deviceId"), deviceId));
        }
        return specification;
    }

    private ValidatedMemory validate(MemoryCommand command) {
        if (command.scopeType() == null || command.category() == null) {
            throw new InvalidMemoryException("Memory scope or category is invalid");
        }
        UUID deviceId = validateScope(command.scopeType(), command.deviceId());
        return new ValidatedMemory(
                command.scopeType(),
                deviceId,
                command.category(),
                normalize(command.title(), 120, "Memory title is invalid", false),
                normalize(command.content(), 2000, "Memory content is invalid", false)
        );
    }

    private UUID validateScope(MemoryScopeType scopeType, UUID deviceId) {
        if (scopeType == MemoryScopeType.GLOBAL) {
            if (deviceId != null) {
                throw new InvalidMemoryException("Global memory cannot target a device");
            }
            return null;
        }
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new InvalidMemoryException("Device memory scope is invalid");
        }
        return deviceId;
    }

    private void validateScopeFilter(MemoryScopeType scopeType, UUID deviceId) {
        if (deviceId != null && scopeType != MemoryScopeType.DEVICE) {
            throw new InvalidMemoryException("Device filter requires device scope");
        }
    }

    private String normalize(String value, int maxLength, String error, boolean allowBlank) {
        String normalized = value == null ? "" : value.trim();
        if ((!allowBlank && normalized.isBlank()) || normalized.length() > maxLength) {
            throw new InvalidMemoryException(error);
        }
        return normalized;
    }

    private LongTermMemoryEntity find(UUID id) {
        return repository.findById(id).orElseThrow(MemoryNotFoundException::new);
    }

    private MemorySnapshot toSnapshot(LongTermMemoryEntity memory) {
        return new MemorySnapshot(
                memory.getId(),
                memory.getScopeType(),
                memory.getDeviceId(),
                memory.getCategory(),
                memory.getTitle(),
                memory.getContent(),
                memory.getSource(),
                memory.getSourceDetail(),
                memory.getConfirmationStatus(),
                memory.isEnabled(),
                memory.getConfirmedAt(),
                memory.getCreatedAt(),
                memory.getUpdatedAt()
        );
    }

    public record MemoryCommand(
            MemoryScopeType scopeType,
            UUID deviceId,
            MemoryCategory category,
            String title,
            String content
    ) {
    }

    public record MemorySuggestionCommand(MemoryCommand memory, String sourceDetail) {
    }

    public record MemorySnapshot(
            UUID id,
            MemoryScopeType scopeType,
            UUID deviceId,
            MemoryCategory category,
            String title,
            String content,
            MemorySource source,
            String sourceDetail,
            MemoryConfirmationStatus confirmationStatus,
            boolean enabled,
            Instant confirmedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record MemoryPage(List<MemorySnapshot> list, long total) {
    }

    private record ValidatedMemory(
            MemoryScopeType scopeType,
            UUID deviceId,
            MemoryCategory category,
            String title,
            String content
    ) {
    }
}
