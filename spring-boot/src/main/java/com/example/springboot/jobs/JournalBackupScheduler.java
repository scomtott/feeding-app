package com.example.springboot.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.springboot.services.JournalOneDriveBackupService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JournalBackupScheduler {

    private final JournalOneDriveBackupService backupService;

    @Scheduled(cron = "${journal.backup.onedrive.cron:0 0 * * * *}")
    public void runHourlyBackup() {
        backupService.runScheduledBackup();
    }
}
