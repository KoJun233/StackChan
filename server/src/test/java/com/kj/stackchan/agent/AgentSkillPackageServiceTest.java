package com.kj.stackchan.agent;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.kj.stackchan.config.AppProperties;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSkillPackageServiceTest {

    private Path tempDirectory;

    @BeforeEach
    void createTempDirectory() throws Exception {
        tempDirectory = Path.of("target", "test-skills", UUID.randomUUID().toString()).toAbsolutePath();
        Files.createDirectories(tempDirectory);
    }

    @AfterEach
    void removeTempDirectory() throws Exception {
        if (!Files.exists(tempDirectory)) {
            return;
        }
        try (var paths = Files.walk(tempDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void importsACompleteSingleDirectoryPackageDisabledAndReloadsTheRegistry() throws Exception {
        Fixture fixture = fixture();
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("daily-routine/SKILL.md", skill("daily-routine", "Daily routine guidance"));
        files.put("daily-routine/references/checklist.md", "# Checklist".getBytes(StandardCharsets.UTF_8));

        AgentSkillPackageService.SkillSnapshot result = fixture.service().importArchive(archive(files));

        assertThat(result.name()).isEqualTo("daily-routine");
        assertThat(result.enabled()).isFalse();
        assertThat(result.files()).containsExactly("SKILL.md", "references/checklist.md");
        assertThat(Files.readString(tempDirectory.resolve("daily-routine/references/checklist.md")))
                .isEqualTo("# Checklist");
        assertThat(fixture.registry().contains("daily-routine")).isTrue();
    }

    @Test
    void acceptsSkillMdAtTheArchiveRoot() {
        Fixture fixture = fixture();
        Map<String, byte[]> files = Map.of("SKILL.md", skill("root-skill", "Root package"));

        AgentSkillPackageService.SkillSnapshot result = fixture.service().importArchive(archive(files));

        assertThat(result.name()).isEqualTo("root-skill");
        assertThat(tempDirectory.resolve("root-skill/SKILL.md")).exists();
    }

    @Test
    void rejectsTraversalAndLeavesNoStagingDirectory() throws Exception {
        Fixture fixture = fixture();
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("../outside.txt", "no".getBytes(StandardCharsets.UTF_8));
        files.put("SKILL.md", skill("unsafe-skill", "Unsafe"));

        assertThatThrownBy(() -> fixture.service().importArchive(archive(files)))
                .isInstanceOf(InvalidAgentSkillException.class)
                .hasMessageContaining("非法路径");
        try (var paths = Files.list(tempDirectory)) {
            assertThat(paths).isEmpty();
        }
    }

    @Test
    void rejectsAbsolutePathsAndSymbolicLinks() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> fixture.service().importArchive(archive(Map.of(
                "C:/outside.txt", "no".getBytes(StandardCharsets.UTF_8),
                "SKILL.md", skill("absolute-skill", "Absolute")
        )))).isInstanceOf(InvalidAgentSkillException.class)
                .hasMessageContaining("非法路径");

        assertThatThrownBy(() -> fixture.service().importArchive(symbolicLinkArchive()))
                .isInstanceOf(InvalidAgentSkillException.class)
                .hasMessageContaining("符号链接");
    }

    @Test
    void rejectsMissingOrMultipleSkillManifests() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.service().importArchive(archive(Map.of(
                "README.md", "missing".getBytes(StandardCharsets.UTF_8)
        )))).isInstanceOf(InvalidAgentSkillException.class)
                .hasMessageContaining("一个 SKILL.md");

        assertThatThrownBy(() -> fixture.service().importArchive(archive(Map.of(
                "one/SKILL.md", skill("one", "One"),
                "two/SKILL.md", skill("two", "Two")
        )))).isInstanceOf(InvalidAgentSkillException.class)
                .hasMessageContaining("一个 SKILL.md");
    }

    @Test
    void rejectsInvalidMetadataAndDuplicateNames() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> fixture.service().importArchive(archive(Map.of(
                "SKILL.md", skill("Invalid_Name", "Invalid")
        )))).isInstanceOf(InvalidAgentSkillException.class)
                .hasMessageContaining("Skill name");

        when(fixture.repository().findByName("daily-routine")).thenReturn(Optional.of(mock(AgentSkillEntity.class)));
        assertThatThrownBy(() -> fixture.service().importArchive(archive(Map.of(
                "SKILL.md", skill("daily-routine", "Duplicate")
        )))).isInstanceOf(InvalidAgentSkillException.class)
                .hasMessageContaining("同名 Skill");
    }

    @Test
    void removesTheInstalledDirectoryWhenMetadataPersistenceFails() {
        Fixture fixture = fixture();
        when(fixture.repository().saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> fixture.service().importArchive(archive(Map.of(
                "SKILL.md", skill("rollback-skill", "Rollback")
        )))).isInstanceOf(InvalidAgentSkillException.class)
                .hasMessageContaining("导入失败");
        assertThat(tempDirectory.resolve("rollback-skill")).doesNotExist();
    }

    @Test
    void enforcesFileCountAndUncompressedSizeLimits() {
        AppProperties properties = properties();
        properties.getAgent().setMaxSkillFileCount(1);
        Fixture fileCountFixture = fixture(properties);
        assertThatThrownBy(() -> fileCountFixture.service().importArchive(archive(Map.of(
                "SKILL.md", skill("limited-skill", "Limited"),
                "reference.md", "extra".getBytes(StandardCharsets.UTF_8)
        )))).isInstanceOf(InvalidAgentSkillException.class)
                .hasMessageContaining("文件数量");

        properties = properties();
        properties.getAgent().setMaxSkillFileBytes(1024);
        Fixture fileSizeFixture = fixture(properties);
        assertThatThrownBy(() -> fileSizeFixture.service().importArchive(archive(Map.of(
                "SKILL.md", skill("large-skill", "x".repeat(1500))
        )))).isInstanceOf(InvalidAgentSkillException.class)
                .hasMessageContaining("大小超过限制");
    }

    private Fixture fixture() {
        return fixture(properties());
    }

    private Fixture fixture(AppProperties properties) {
        AgentSkillRepository repository = mock(AgentSkillRepository.class);
        when(repository.findByName(any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        FileSystemSkillRegistry registry = FileSystemSkillRegistry.builder()
                .userSkillsDirectory(tempDirectory.resolve(".none").toString())
                .projectSkillsDirectory(tempDirectory.toString())
                .build();
        AgentSkillPackageService service = new AgentSkillPackageService(
                repository,
                settingsService,
                registry,
                properties,
                Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC),
                transactionManager
        );
        return new Fixture(service, repository, registry);
    }

    private AppProperties properties() {
        AppProperties properties = new AppProperties();
        properties.getAgent().setSkillsDirectory(tempDirectory.toString());
        return properties;
    }

    private static MockMultipartFile archive(Map<String, byte[]> files) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for (Map.Entry<String, byte[]> file : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(file.getKey()));
                    zip.write(file.getValue());
                    zip.closeEntry();
                }
            }
            return new MockMultipartFile("archive", "skill.zip", "application/zip", bytes.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static MockMultipartFile symbolicLinkArchive() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(bytes)) {
                ZipArchiveEntry manifest = new ZipArchiveEntry("SKILL.md");
                zip.putArchiveEntry(manifest);
                zip.write(skill("link-skill", "Link"));
                zip.closeArchiveEntry();
                ZipArchiveEntry link = new ZipArchiveEntry("references/current.md");
                link.setUnixMode(0120777);
                zip.putArchiveEntry(link);
                zip.write("target.md".getBytes(StandardCharsets.UTF_8));
                zip.closeArchiveEntry();
            }
            return new MockMultipartFile("archive", "skill.zip", "application/zip", bytes.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] skill(String name, String description) {
        return ("""
                ---
                name: %s
                description: %s
                version: "1.0"
                ---

                Follow the imported guidance.
                """).formatted(name, description).getBytes(StandardCharsets.UTF_8);
    }

    private record Fixture(
            AgentSkillPackageService service,
            AgentSkillRepository repository,
            FileSystemSkillRegistry registry
    ) { }
}
