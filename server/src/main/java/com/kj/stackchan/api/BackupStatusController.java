package com.kj.stackchan.api;

import com.kj.stackchan.backup.BackupStatusService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/personal-data/backups", produces = MediaType.APPLICATION_JSON_VALUE)
public class BackupStatusController {

    private final BackupStatusService backupStatusService;

    public BackupStatusController(BackupStatusService backupStatusService) {
        this.backupStatusService = backupStatusService;
    }

    @GetMapping("/status")
    public BackupStatusService.BackupStatus status() {
        return backupStatusService.status();
    }
}
