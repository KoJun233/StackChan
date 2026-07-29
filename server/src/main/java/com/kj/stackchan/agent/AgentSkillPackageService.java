package com.kj.stackchan.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.kj.stackchan.config.AppProperties;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@Service
public class AgentSkillPackageService {

    private static final Pattern SKILL_NAME = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 1024;
    private static final int MAX_VERSION_LENGTH = 64;
    private static final int MAX_DIRECTORY_DEPTH = 8;
    private static final Set<String> ARCHIVE_CONTENT_TYPES = Set.of(
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream"
    );

    private final AgentSkillRepository repository;
    private final AgentSettingsService settingsService;
    private final FileSystemSkillRegistry registry;
    private final AppProperties appProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final Path skillsRoot;

    public AgentSkillPackageService(
            AgentSkillRepository repository,
            AgentSettingsService settingsService,
            FileSystemSkillRegistry registry,
            AppProperties appProperties,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.settingsService = settingsService;
        this.registry = registry;
        this.appProperties = appProperties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.skillsRoot = Path.of(appProperties.getAgent().getSkillsDirectory()).toAbsolutePath().normalize();
        createSkillsRoot();
    }

    public synchronized SkillSnapshot importArchive(MultipartFile archive) {
        validateArchive(archive);
        UUID importId = UUID.randomUUID();
        Path staging = skillsRoot.resolve(".staging-" + importId).normalize();
        Path upload = skillsRoot.resolve(".upload-" + importId + ".zip").normalize();
        Path installed = null;
        try {
            Files.createDirectory(staging);
            copyArchiveLimited(archive, upload);
            Extraction extraction = extract(upload, staging);
            Files.deleteIfExists(upload);
            Path packageRoot = locatePackageRoot(staging, extraction.files());
            SkillManifest manifest = readManifest(packageRoot.resolve("SKILL.md"));
            if (repository.findByName(manifest.name()).isPresent()) {
                throw new InvalidAgentSkillException("已存在同名 Skill，请先删除旧版本");
            }

            installed = skillsRoot.resolve(manifest.name()).normalize();
            requireDirectChild(installed);
            if (Files.exists(installed)) {
                throw new InvalidAgentSkillException("Skill 目录已存在，请检查持久化目录");
            }
            String sha256 = packageDigest(packageRoot);
            movePackage(packageRoot, installed);
            Path finalInstalled = installed;
            AgentSkillEntity saved = transactionTemplate.execute(status -> repository.saveAndFlush(new AgentSkillEntity(
                    manifest.name(),
                    manifest.description(),
                    manifest.version(),
                    manifest.name(),
                    sha256,
                    extraction.fileCount(),
                    extraction.totalBytes(),
                    clock.instant()
            )));
            if (saved == null) {
                throw new InvalidAgentSkillException("Skill 元数据保存失败");
            }
            registry.reload();
            return snapshot(saved);
        } catch (InvalidAgentSkillException exception) {
            deleteQuietly(installed);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            deleteQuietly(installed);
            throw new InvalidAgentSkillException("Skill 压缩包导入失败", exception);
        } finally {
            deleteQuietly(upload);
            deleteQuietly(staging);
        }
    }

    public List<SkillSnapshot> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::snapshot).toList();
    }

    public Set<String> enabledNames() {
        return repository.findAllByEnabledTrueOrderByNameAsc().stream()
                .filter(skill -> registry.contains(skill.getName()))
                .map(AgentSkillEntity::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public synchronized SkillSnapshot setEnabled(UUID id, boolean enabled) {
        AgentSkillEntity entity = transactionTemplate.execute(status -> {
            AgentSkillEntity skill = repository.findById(id).orElseThrow(AgentSkillNotFoundException::new);
            if (enabled && !registry.contains(skill.getName())) {
                throw new InvalidAgentSkillException("Skill 文件缺失或无法解析，不能启用");
            }
            skill.setEnabled(enabled, clock.instant());
            settingsService.updateCapability(AgentCapabilityType.SKILL, skill.getName(), enabled, null, null);
            return repository.save(skill);
        });
        return snapshot(entity);
    }

    public synchronized void delete(UUID id) {
        AgentSkillEntity entity = repository.findById(id).orElseThrow(AgentSkillNotFoundException::new);
        if (entity.isEnabled()) {
            throw new InvalidAgentSkillException("请先停用 Skill 再删除");
        }
        Path directory = skillsRoot.resolve(entity.getDirectoryName()).normalize();
        requireDirectChild(directory);
        Path trash = skillsRoot.resolve(".trash").resolve(UUID.randomUUID().toString()).normalize();
        try {
            if (Files.exists(directory)) {
                Files.createDirectories(trash.getParent());
                movePackage(directory, trash);
            }
            transactionTemplate.executeWithoutResult(status -> {
                AgentSkillEntity current = repository.findById(id).orElseThrow(AgentSkillNotFoundException::new);
                if (current.isEnabled()) {
                    throw new InvalidAgentSkillException("请先停用 Skill 再删除");
                }
                repository.delete(current);
                settingsService.updateCapability(AgentCapabilityType.SKILL, current.getName(), false, null, null);
            });
            deleteQuietly(trash);
            registry.reload();
        } catch (IOException | RuntimeException exception) {
            if (Files.exists(trash) && !Files.exists(directory)) {
                try {
                    movePackage(trash, directory);
                } catch (IOException ignored) {
                }
            }
            if (exception instanceof InvalidAgentSkillException invalid) {
                throw invalid;
            }
            throw new InvalidAgentSkillException("Skill 删除失败", exception);
        }
    }

    private Extraction extract(Path archive, Path staging) throws IOException {
        int fileCount = 0;
        long totalBytes = 0;
        List<Path> files = new java.util.ArrayList<>();
        Set<Path> seen = new java.util.HashSet<>();
        try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String entryName = normalizeEntryName(entry.getName());
                Path relative = Path.of(entryName).normalize();
                if (relative.isAbsolute() || relative.startsWith("..") || relative.getNameCount() > MAX_DIRECTORY_DEPTH) {
                    throw new InvalidAgentSkillException("压缩包包含非法路径");
                }
                Path target = staging.resolve(relative).normalize();
                if (!target.startsWith(staging) || !seen.add(target)) {
                    throw new InvalidAgentSkillException("压缩包包含重复或越界路径");
                }
                if (entry.isUnixSymlink()) {
                    throw new InvalidAgentSkillException("压缩包不能包含符号链接");
                }
                if (entry.getGeneralPurposeBit().usesEncryption()) {
                    throw new InvalidAgentSkillException("压缩包不能包含加密文件");
                }
                int fileType = entry.getUnixMode() & 0170000;
                if (fileType != 0 && fileType != 0100000 && fileType != 0040000) {
                    throw new InvalidAgentSkillException("压缩包不能包含非普通文件");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                fileCount++;
                if (fileCount > appProperties.getAgent().getMaxSkillFileCount()) {
                    throw new InvalidAgentSkillException("压缩包文件数量超过限制");
                }
                Files.createDirectories(target.getParent());
                long fileBytes;
                try (InputStream entryInput = zip.getInputStream(entry)) {
                    fileBytes = copyLimited(entryInput, target, totalBytes);
                }
                totalBytes += fileBytes;
                files.add(target);
            }
        }
        if (fileCount == 0 || totalBytes == 0) {
            throw new InvalidAgentSkillException("压缩包为空");
        }
        return new Extraction(List.copyOf(files), fileCount, totalBytes);
    }

    private void copyArchiveLimited(MultipartFile archive, Path target) throws IOException {
        byte[] buffer = new byte[8192];
        long copied = 0;
        try (InputStream input = archive.getInputStream(); var output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                copied += read;
                if (copied > appProperties.getAgent().getMaxSkillArchiveBytes()) {
                    throw new InvalidAgentSkillException("Skill ZIP 超过上传大小限制");
                }
                output.write(buffer, 0, read);
            }
        }
        if (copied == 0) {
            throw new InvalidAgentSkillException("Skill ZIP 为空");
        }
    }

    private long copyLimited(InputStream input, Path target, long previousTotal) throws IOException {
        byte[] buffer = new byte[8192];
        long fileBytes = 0;
        try (var output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                fileBytes += read;
                if (fileBytes > appProperties.getAgent().getMaxSkillFileBytes()
                        || previousTotal + fileBytes > appProperties.getAgent().getMaxSkillUncompressedBytes()) {
                    throw new InvalidAgentSkillException("压缩包解压后大小超过限制");
                }
                output.write(buffer, 0, read);
            }
        }
        return fileBytes;
    }

    private Path locatePackageRoot(Path staging, List<Path> files) {
        List<Path> skillFiles = files.stream()
                .filter(path -> path.getFileName().toString().equals("SKILL.md"))
                .toList();
        if (skillFiles.size() != 1) {
            throw new InvalidAgentSkillException("压缩包必须且只能包含一个 SKILL.md");
        }
        Path parent = skillFiles.getFirst().getParent();
        Path relativeParent = staging.relativize(parent);
        if (relativeParent.getNameCount() > 1) {
            throw new InvalidAgentSkillException("SKILL.md 必须位于压缩包根目录或唯一顶层目录");
        }
        if (!parent.equals(staging)) {
            boolean outsideRoot = files.stream().anyMatch(path -> !path.startsWith(parent));
            if (outsideRoot) {
                throw new InvalidAgentSkillException("压缩包只能包含一个顶层 Skill 目录");
            }
        }
        return parent;
    }

    private SkillManifest readManifest(Path skillFile) throws IOException {
        long size = Files.size(skillFile);
        if (size == 0 || size > Math.min(524288, appProperties.getAgent().getMaxSkillFileBytes())) {
            throw new InvalidAgentSkillException("SKILL.md 大小不合法");
        }
        String content = Files.readString(skillFile, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!content.startsWith("---\n")) {
            throw new InvalidAgentSkillException("SKILL.md 缺少 YAML frontmatter");
        }
        int end = content.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new InvalidAgentSkillException("SKILL.md frontmatter 未闭合");
        }
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(10);
        Object parsed;
        try {
            parsed = new Yaml(new SafeConstructor(options)).load(content.substring(4, end));
        } catch (RuntimeException exception) {
            throw new InvalidAgentSkillException("SKILL.md frontmatter 无法解析", exception);
        }
        if (!(parsed instanceof Map<?, ?> values)) {
            throw new InvalidAgentSkillException("SKILL.md frontmatter 必须是对象");
        }
        String name = requiredString(values, "name", MAX_NAME_LENGTH);
        String description = requiredString(values, "description", MAX_DESCRIPTION_LENGTH);
        String version = optionalString(values, "version", MAX_VERSION_LENGTH);
        if (!SKILL_NAME.matcher(name).matches()) {
            throw new InvalidAgentSkillException("Skill name 必须是小写字母、数字和单连字符，且不超过 64 字符");
        }
        return new SkillManifest(name, description, version);
    }

    private SkillSnapshot snapshot(AgentSkillEntity entity) {
        Path directory = skillsRoot.resolve(entity.getDirectoryName()).normalize();
        List<String> files = List.of();
        if (directory.startsWith(skillsRoot) && Files.isDirectory(directory)) {
            try (Stream<Path> paths = Files.walk(directory)) {
                files = paths.filter(Files::isRegularFile)
                        .map(directory::relativize)
                        .map(path -> path.toString().replace('\\', '/'))
                        .sorted()
                        .toList();
            } catch (IOException ignored) {
                files = List.of();
            }
        }
        return new SkillSnapshot(
                entity.getId(), entity.getName(), entity.getDescription(), entity.getVersion(), entity.isEnabled(),
                entity.getContentSha256(), entity.getFileCount(), entity.getUncompressedBytes(), files,
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private String packageDigest(Path packageRoot) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        try (Stream<Path> paths = Files.walk(packageRoot)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                String relative = packageRoot.relativize(path).toString().replace('\\', '/');
                digest.update(relative.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                try (InputStream input = Files.newInputStream(path)) {
                    input.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest));
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void validateArchive(MultipartFile archive) {
        if (archive == null || archive.isEmpty() || archive.getSize() > appProperties.getAgent().getMaxSkillArchiveBytes()) {
            throw new InvalidAgentSkillException("请选择不超过导入上限的 ZIP 压缩包");
        }
        String filename = archive.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            throw new InvalidAgentSkillException("仅支持 .zip Skill 压缩包");
        }
        String contentType = archive.getContentType();
        if (contentType != null && !contentType.isBlank() && !ARCHIVE_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidAgentSkillException("压缩包 Content-Type 不受支持");
        }
    }

    private static String normalizeEntryName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || name.indexOf(':') >= 0) {
            throw new InvalidAgentSkillException("压缩包包含非法路径");
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new InvalidAgentSkillException("压缩包包含绝对路径");
        }
        return normalized;
    }

    private static String requiredString(Map<?, ?> values, String key, int maximum) {
        String result = optionalString(values, key, maximum);
        if (result == null) {
            throw new InvalidAgentSkillException("SKILL.md 缺少 " + key);
        }
        return result;
    }

    private static String optionalString(Map<?, ?> values, String key, int maximum) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String string)) {
            throw new InvalidAgentSkillException("SKILL.md 的 " + key + " 必须是字符串");
        }
        String normalized = string.strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new InvalidAgentSkillException("SKILL.md 的 " + key + " 长度不合法");
        }
        return normalized;
    }

    private void createSkillsRoot() {
        try {
            Files.createDirectories(skillsRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create Agent skills directory", exception);
        }
    }

    private void movePackage(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void requireDirectChild(Path path) {
        if (!path.getParent().equals(skillsRoot)) {
            throw new IllegalStateException("Agent skill path escaped configured directory");
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            deleteTree(path);
        } catch (IOException ignored) {
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record Extraction(List<Path> files, int fileCount, long totalBytes) { }
    private record SkillManifest(String name, String description, String version) { }

    public record SkillSnapshot(
            UUID id,
            String name,
            String description,
            String version,
            boolean enabled,
            String contentSha256,
            int fileCount,
            long uncompressedBytes,
            List<String> files,
            Instant createdAt,
            Instant updatedAt
    ) { }
}
