package com.example.springboot.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.springboot.logging.BufferedBackendFileLogger;
import com.example.springboot.models.LogLevel;
import com.example.springboot.services.JournalOneDriveBackupService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JournalBackupScheduler {

    private final JournalOneDriveBackupService backupService;
    private final BufferedBackendFileLogger backendFileLogger;

    @Scheduled(cron = "${journal.backup.onedrive.cron:0 0 * * * *}")
    public void runHourlyBackup() {
        backendFileLogger.log(
            JournalBackupScheduler.class,
            LogLevel.INFO,
            "scheduled-job",
            "Scheduled job running: journal-backup"
        );
        backupService.runScheduledBackup();
    }
}
