package com.kj.stackchan.memory;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.kj.stackchan.role.CompanionRoleEntity;

@Service
public class LongTermMemoryService {

    public static final String USER_ENTERED_DETAIL = "由管理员在控制台明确添加";

    private final LongTermMemoryRepository repository;
    private final DeviceRepository deviceRepository;
    private final MemoryUsageRepository usageRepository;
    private final MemorySuggestionSafetyPolicy safetyPolicy;
    private final Clock clock;

    @Autowired
    public LongTermMemoryService(
            LongTermMemoryRepository repository,
            DeviceRepository deviceRepository,
            MemoryUsageRepository usageRepository,
            MemorySuggestionSafetyPolicy safetyPolicy,
            Clock clock
    ) {
        this.repository = repository;
        this.deviceRepository = deviceRepository;
        this.usageRepository = usageRepository;
        this.safetyPolicy = safetyPolicy;
        this.clock = clock;
    }

    public LongTermMemoryService(
            LongTermMemoryRepository repository,
            DeviceRepository deviceRepository,
            Clock clock
    ) {
        this(repository, deviceRepository, null, new MemorySuggestionSafetyPolicy(), clock);
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
        return list(CompanionRoleEntity.DEFAULT_ROLE_ID, query, category, confirmationStatus,
                enabled, scopeType, deviceId, from, limit);
    }

    @Transactional(readOnly = true)
    public MemoryPage list(UUID roleId, String query, MemoryCategory category,
                           MemoryConfirmationStatus confirmationStatus, Boolean enabled,
                           MemoryScopeType scopeType, UUID deviceId, int from, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int page = Math.max(from, 0) / safeLimit;
        Specification<LongTermMemoryEntity> specification = baseSpecification(
                roleId, query, category, confirmationStatus, enabled, scopeType, deviceId
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
        return create(CompanionRoleEntity.DEFAULT_ROLE_ID, command);
    }

    @Transactional
    public MemorySnapshot create(UUID roleId, MemoryCommand command) {
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
                validated.topicKey(),
                validated.importance(),
                null,
                null,
                validated.allowProactiveMention(),
                now
        );
        memory.assignRole(roleId);
        return toSnapshot(repository.save(memory));
    }

    @Transactional
    public MemorySnapshot suggest(MemorySuggestionCommand command) {
        return suggest(CompanionRoleEntity.DEFAULT_ROLE_ID, command);
    }

    @Transactional
    public MemorySnapshot suggest(UUID roleId, MemorySuggestionCommand command) {
        ValidatedMemory validated = validate(command.memory());
        String sourceDetail = normalize(command.sourceDetail(), 500, "Memory suggestion reason is invalid", false);
        if (!safetyPolicy.isAllowed(validated.title(), validated.content(), sourceDetail)) {
            throw new InvalidMemoryException("Sensitive memory suggestion is not allowed");
        }
        Instant now = clock.instant();
        UUID replacesMemoryId = findReplacement(roleId, validated).stream().findFirst()
                .map(LongTermMemoryEntity::getId)
                .orElse(null);
        LongTermMemoryEntity memory = new LongTermMemoryEntity(
                validated.scopeType(),
                validated.deviceId(),
                validated.category(),
                validated.title(),
                validated.content(),
                MemorySource.ASSISTANT_SUGGESTED,
                sourceDetail,
                MemoryConfirmationStatus.PENDING,
                validated.topicKey(),
                validated.importance(),
                command.sourceTurnId(),
                replacesMemoryId,
                false,
                now
        );
        memory.assignRole(roleId);
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
                validated.topicKey(),
                validated.importance(),
                validated.allowProactiveMention(),
                clock.instant()
        );
        if (memory.getConfirmationStatus() == MemoryConfirmationStatus.PENDING
                && memory.getSource() == MemorySource.ASSISTANT_SUGGESTED) {
            UUID replacementId = findReplacement(memory.getRoleId(), validated).stream().findFirst()
                    .map(LongTermMemoryEntity::getId)
                    .orElse(null);
            memory.setReplacementCandidate(replacementId, clock.instant());
        }
        return toSnapshot(memory);
    }

    @Transactional
    public MemorySnapshot confirm(UUID id) {
        LongTermMemoryEntity memory = repository.findByIdForUpdate(id).orElseThrow(MemoryNotFoundException::new);
        Instant now = clock.instant();
        if (memory.getConfirmationStatus() == MemoryConfirmationStatus.PENDING
                && memory.getReplacesMemoryId() != null) {
            repository.findByIdForUpdate(memory.getReplacesMemoryId())
                    .filter(existing -> existing.getConfirmationStatus() == MemoryConfirmationStatus.CONFIRMED)
                    .filter(LongTermMemoryEntity::isEnabled)
                    .filter(existing -> existing.getSupersededByMemoryId() == null)
                    .ifPresent(existing -> existing.markSuperseded(memory.getId(), now));
        }
        memory.confirm(now);
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
        if (enabled && memory.getSupersededByMemoryId() != null) {
            throw new InvalidMemoryException("Superseded memory cannot be enabled");
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
        return clear(CompanionRoleEntity.DEFAULT_ROLE_ID, scopeType, deviceId);
    }

    @Transactional
    public long clear(UUID roleId, MemoryScopeType scopeType, UUID deviceId) {
        validateScopeFilter(scopeType, deviceId);
        List<LongTermMemoryEntity> memories = repository.findAll(baseSpecification(
                roleId, "", null, null, null, scopeType, deviceId
        ));
        repository.deleteAll(memories);
        return memories.size();
    }

    @Transactional(readOnly = true)
    public List<MemorySnapshot> loadContext(UUID deviceId, int limit) {
        return loadContext(CompanionRoleEntity.DEFAULT_ROLE_ID, deviceId, "", limit);
    }

    @Transactional(readOnly = true)
    public List<MemorySnapshot> loadContext(UUID deviceId, String queryText, int limit) {
        return loadContext(CompanionRoleEntity.DEFAULT_ROLE_ID, deviceId, queryText, limit);
    }

    @Transactional(readOnly = true)
    public List<MemorySnapshot> loadContext(UUID roleId, UUID deviceId, String queryText, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 8);
        try {
            List<LongTermMemoryEntity> matches = repository.searchContext(
                    roleId, deviceId, normalizeSearchQuery(queryText), safeLimit
            );
            if (matches == null) {
                return loadContextFallback(roleId, deviceId, safeLimit);
            }
            return matches.stream()
                    .map(this::toSnapshot)
                    .toList();
        } catch (DataAccessException ignored) {
            return loadContextFallback(roleId, deviceId, safeLimit);
        }
    }

    @Transactional(readOnly = true)
    public List<MemorySnapshot> loadProactiveCandidates(UUID deviceId, int limit) {
        return loadProactiveCandidates(CompanionRoleEntity.DEFAULT_ROLE_ID, deviceId, limit);
    }

    @Transactional(readOnly = true)
    public List<MemorySnapshot> loadProactiveCandidates(UUID roleId, UUID deviceId, int limit) {
        if (deviceId == null) return List.of();
        int safeLimit = Math.min(Math.max(limit, 1), 8);
        return repository.findProactiveCandidates(roleId, deviceId, PageRequest.of(0, safeLimit)).stream()
                .filter(memory -> safetyPolicy.isAllowed(
                        memory.getTitle(), memory.getContent(), memory.getSourceDetail()
                ))
                .map(this::toSnapshot)
                .toList();
    }

    private List<MemorySnapshot> loadContextFallback(UUID roleId, UUID deviceId, int limit) {
        Specification<LongTermMemoryEntity> specification = (root, query, builder) -> builder.and(
                builder.equal(root.get("confirmationStatus"), MemoryConfirmationStatus.CONFIRMED),
                builder.equal(root.get("roleId"), roleId),
                builder.isTrue(root.get("enabled")),
                builder.isNull(root.get("supersededByMemoryId")),
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
                        PageRequest.of(0, limit,
                                Sort.by(Sort.Direction.DESC, "importance", "updatedAt", "id"))
                )
                .getContent()
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Transactional
    public void recordUsage(UUID turnId, List<UUID> memoryIds) {
        if (turnId == null || usageRepository == null || memoryIds == null || memoryIds.isEmpty()) {
            return;
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>(memoryIds);
        List<LongTermMemoryEntity> memories = repository.findAllById(uniqueIds);
        Instant now = clock.instant();
        for (LongTermMemoryEntity memory : memories) {
            MemoryUsageId usageId = new MemoryUsageId(turnId, memory.getId());
            if (usageRepository.existsById(usageId)) {
                continue;
            }
            usageRepository.save(new MemoryUsageEntity(turnId, memory.getId(), now));
            memory.markUsed(now);
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> usageForTurn(UUID turnId) {
        if (usageRepository == null) {
            return List.of();
        }
        return usageRepository.findAllByTurnIdOrderByMemoryId(turnId).stream()
                .map(MemoryUsageEntity::getMemoryId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemoryUsageReference> usageReferencesForTurn(UUID turnId) {
        List<UUID> ids = usageForTurn(turnId);
        if (ids.isEmpty()) {
            return List.of();
        }
        var byId = repository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(LongTermMemoryEntity::getId, memory -> memory));
        return ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(memory -> new MemoryUsageReference(
                        memory.getId(),
                        memory.getTitle(),
                        memory.getTopicKey(),
                        memory.getScopeType(),
                        memory.getSource(),
                        memory.getSourceDetail()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public long pendingVisibleCount(UUID deviceId) {
        return pendingVisibleCount(CompanionRoleEntity.DEFAULT_ROLE_ID, deviceId);
    }

    @Transactional(readOnly = true)
    public long pendingVisibleCount(UUID roleId, UUID deviceId) {
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new InvalidMemoryException("Memory device scope is invalid");
        }
        return repository.countVisibleByRoleAndDeviceAndStatus(roleId, deviceId, MemoryConfirmationStatus.PENDING);
    }

    private Specification<LongTermMemoryEntity> baseSpecification(
            UUID roleId,
            String queryText,
            MemoryCategory category,
            MemoryConfirmationStatus confirmationStatus,
            Boolean enabled,
            MemoryScopeType scopeType,
            UUID deviceId
    ) {
        validateScopeFilter(scopeType, deviceId);
        Specification<LongTermMemoryEntity> specification = (root, query, builder) -> builder.conjunction();
        specification = specification.and((root, query, builder) -> builder.equal(root.get("roleId"), roleId));
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
                normalize(command.content(), 2000, "Memory content is invalid", false),
                normalizeTopicKey(command.topicKey(), command.title()),
                validateImportance(command.importance()),
                command.allowProactiveMention()
        );
    }

    private List<LongTermMemoryEntity> findReplacement(UUID roleId, ValidatedMemory memory) {
        List<LongTermMemoryEntity> matches = repository.findActiveTopicMatches(
                roleId, memory.topicKey(), memory.scopeType(), memory.deviceId()
        );
        return matches == null ? List.of() : matches;
    }

    private String normalizeTopicKey(String topicKey, String title) {
        String candidate = topicKey == null || topicKey.isBlank() ? title : topicKey;
        return normalize(candidate, 120, "Memory topic key is invalid", false)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private int validateImportance(Integer importance) {
        int value = importance == null ? 3 : importance;
        if (value < 1 || value > 5) {
            throw new InvalidMemoryException("Memory importance is invalid");
        }
        return value;
    }

    private String normalizeSearchQuery(String queryText) {
        String normalized = queryText == null ? "" : queryText.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
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
        List<LongTermMemoryEntity> duplicateMatches = repository.findPossibleDuplicates(
                memory.getRoleId(), memory.getTopicKey(), memory.getScopeType(), memory.getDeviceId(), memory.getId()
        );
        List<UUID> duplicateIds = duplicateMatches == null ? List.of() : duplicateMatches.stream()
                .limit(5)
                .map(LongTermMemoryEntity::getId)
                .toList();
        return new MemorySnapshot(
                memory.getId(),
                memory.getRoleId(),
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
                memory.getUpdatedAt(),
                memory.getTopicKey(),
                memory.getImportance(),
                memory.getLastUsedAt(),
                memory.getSourceTurnId(),
                memory.getReplacesMemoryId(),
                memory.getSupersededByMemoryId(),
                memory.isAllowProactiveMention(),
                duplicateIds
        );
    }

    public record MemoryCommand(
            MemoryScopeType scopeType,
            UUID deviceId,
            MemoryCategory category,
            String title,
            String content,
            String topicKey,
            Integer importance,
            boolean allowProactiveMention
    ) {
        public MemoryCommand(
                MemoryScopeType scopeType,
                UUID deviceId,
                MemoryCategory category,
                String title,
                String content
        ) {
            this(scopeType, deviceId, category, title, content, title, 3, false);
        }
    }

    public record MemorySuggestionCommand(MemoryCommand memory, String sourceDetail, UUID sourceTurnId) {
        public MemorySuggestionCommand(MemoryCommand memory, String sourceDetail) {
            this(memory, sourceDetail, null);
        }
    }

    public record MemorySnapshot(
            UUID id,
            UUID roleId,
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
            Instant updatedAt,
            String topicKey,
            int importance,
            Instant lastUsedAt,
            UUID sourceTurnId,
            UUID replacesMemoryId,
            UUID supersededByMemoryId,
            boolean allowProactiveMention,
            List<UUID> possibleDuplicateIds
    ) {
        public MemorySnapshot(
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
            this(id, CompanionRoleEntity.DEFAULT_ROLE_ID, scopeType, deviceId, category, title, content, source, sourceDetail,
                    confirmationStatus, enabled, confirmedAt, createdAt, updatedAt,
                    title, 3, null, null, null, null, false, List.of());
        }
    }

    public record MemoryPage(List<MemorySnapshot> list, long total) {
    }

    public record MemoryUsageReference(
            UUID memoryId,
            String title,
            String topicKey,
            MemoryScopeType scopeType,
            MemorySource source,
            String sourceDetail
    ) {
    }

    private record ValidatedMemory(
            MemoryScopeType scopeType,
            UUID deviceId,
            MemoryCategory category,
            String title,
            String content,
            String topicKey,
            int importance,
            boolean allowProactiveMention
    ) {
    }
}
