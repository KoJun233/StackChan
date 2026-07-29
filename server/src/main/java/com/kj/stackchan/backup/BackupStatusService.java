package com.kj.stackchan.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.config.AppProperties;
import org.springframework.stereotype.Service;

@Service
public class BackupStatusService {

    private static final int DAILY_RETENTION = 7;
    private static final int WEEKLY_RETENTION = 4;

    private final Path backupDirectory;
    private final ObjectMapper objectMapper;

    public BackupStatusService(AppProperties appProperties, ObjectMapper objectMapper) {
        this.backupDirectory = Path.of(appProperties.getPersonalData().getBackupDirectory())
                .toAbsolutePath()
                .normalize();
        this.objectMapper = objectMapper;
    }

    public BackupStatus status() {
        JsonNode status = readStatus();
        DirectorySummary summary = summarizeDirectory();
        return new BackupStatus(
                status != null,
                instant(status, "lastAttemptAt"),
                instant(status, "lastSuccessfulBackupAt"),
                instant(status, "lastFailureAt"),
                text(status, "lastFailureCode"),
                instant(status, "lastRestoreVerificationAt"),
                bool(status, "lastRestoreVerificationSuccessful"),
                text(status, "lastRestoreVerificationFailureCode"),
                summary.dailyCount(),
                summary.weeklyCount(),
                DAILY_RETENTION,
                WEEKLY_RETENTION,
                summary.storageBytes()
        );
    }

    private JsonNode readStatus() {
        Path statusPath = backupDirectory.resolve("status.json");
        if (!Files.isRegularFile(statusPath, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(statusPath.toFile());
            if (root.path("schemaVersion").asInt(-1) != 1) {
                return null;
            }
            return root;
        } catch (IOException ignored) {
            return null;
        }
    }

    private DirectorySummary summarizeDirectory() {
        if (!Files.isDirectory(backupDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return new DirectorySummary(0, 0, 0);
        }
        try (Stream<Path> paths = Files.walk(backupDirectory)) {
            long[] summary = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> new long[]{
                            isManifest(path, "daily") ? 1 : 0,
                            isManifest(path, "weekly") ? 1 : 0,
                            safeSize(path)
                    })
                    .reduce(new long[3], (left, right) -> new long[]{
                            left[0] + right[0], left[1] + right[1], left[2] + right[2]
                    });
            return new DirectorySummary(summary[0], summary[1], summary[2]);
        } catch (IOException ignored) {
            return new DirectorySummary(0, 0, 0);
        }
    }

    private boolean isManifest(Path path, String directoryName) {
        Path parent = path.getParent();
        return parent != null
                && Objects.equals(parent.getFileName().toString(), directoryName)
                && path.getFileName().toString().endsWith(".manifest.json");
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return 0;
        }
    }

    private Instant instant(JsonNode status, String field) {
        String value = text(status, field);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String text(JsonNode status, String field) {
        if (status == null || !status.hasNonNull(field)) {
            return null;
        }
        String value = status.path(field).asText().strip();
        return value.isEmpty() ? null : value;
    }

    private Boolean bool(JsonNode status, String field) {
        if (status == null || !status.path(field).isBoolean()) {
            return null;
        }
        return status.path(field).booleanValue();
    }

    public record BackupStatus(
            boolean available,
            Instant lastAttemptAt,
            Instant lastSuccessfulBackupAt,
            Instant lastFailureAt,
            String lastFailureCode,
            Instant lastRestoreVerificationAt,
            Boolean lastRestoreVerificationSuccessful,
            String lastRestoreVerificationFailureCode,
            long dailyBackupCount,
            long weeklyBackupCount,
            int dailyRetention,
            int weeklyRetention,
            long storageBytes
    ) {
    }

    private record DirectorySummary(long dailyCount, long weeklyCount, long storageBytes) {
    }
}
