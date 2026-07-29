package com.kj.stackchan.backup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class BackupStatusServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesOnlySafeStatusAndCountsSuccessfulManifests() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve("daily"));
        Files.createDirectories(temporaryDirectory.resolve("weekly"));
        Files.writeString(temporaryDirectory.resolve("daily/daily-1.dump"), "dump");
        Files.writeString(temporaryDirectory.resolve("daily/daily-1.manifest.json"), "{}");
        Files.writeString(temporaryDirectory.resolve("daily/incomplete.dump.partial"), "partial");
        Files.writeString(temporaryDirectory.resolve("weekly/week-1.manifest.json"), "{}");
        Files.writeString(temporaryDirectory.resolve("status.json"), """
                {
                  "schemaVersion": 1,
                  "lastAttemptAt": "2026-07-29T12:00:00Z",
                  "lastSuccessfulBackupAt": "2026-07-29T12:00:00Z",
                  "lastFailureAt": "2026-07-28T12:00:00Z",
                  "lastFailureCode": "DUMP_FAILED",
                  "lastRestoreVerificationAt": "2026-07-29T12:01:00Z",
                  "lastRestoreVerificationSuccessful": true,
                  "lastRestoreVerificationFailureCode": null,
                  "databasePassword": "must-not-be-returned",
                  "backupPath": "/secret/path"
                }
                """);
        AppProperties properties = new AppProperties();
        properties.getPersonalData().setBackupDirectory(temporaryDirectory.toString());

        BackupStatusService.BackupStatus status = new BackupStatusService(properties, new ObjectMapper()).status();

        assertThat(status.available()).isTrue();
        assertThat(status.lastSuccessfulBackupAt()).isEqualTo(Instant.parse("2026-07-29T12:00:00Z"));
        assertThat(status.lastRestoreVerificationSuccessful()).isTrue();
        assertThat(status.dailyBackupCount()).isEqualTo(1);
        assertThat(status.weeklyBackupCount()).isEqualTo(1);
        assertThat(status.dailyRetention()).isEqualTo(7);
        assertThat(status.weeklyRetention()).isEqualTo(4);
        assertThat(status.storageBytes()).isPositive();
        assertThat(status.toString()).doesNotContain("must-not-be-returned", "/secret/path");
    }

    @Test
    void treatsMissingOrMalformedStatusAsUnavailableWithoutFailingThePage() throws Exception {
        AppProperties properties = new AppProperties();
        properties.getPersonalData().setBackupDirectory(temporaryDirectory.toString());
        BackupStatusService service = new BackupStatusService(properties, new ObjectMapper());

        assertThat(service.status().available()).isFalse();

        Files.writeString(temporaryDirectory.resolve("status.json"), "not-json");
        assertThat(service.status().available()).isFalse();
    }
}
