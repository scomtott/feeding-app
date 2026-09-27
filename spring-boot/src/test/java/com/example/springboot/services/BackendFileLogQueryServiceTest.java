package com.example.springboot.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.springboot.models.BackendFileLogResponse;
import com.example.springboot.models.BackendFileLogStorageResponse;

class BackendFileLogQueryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void getLogsByTimeWindowFiltersMatchingEntries() throws Exception {
        writeLogFile(
            "backend-20260829-1.log",
            List.of(
                "2026-08-29T20:00:00.000+01:00 | INFO | feeding | com.example.First | first",
                "2026-08-29T20:05:00.000+01:00 | ERROR | pumping | com.example.Second | second",
                "2026-08-29T20:10:00.000+01:00 | WARN | feeding | com.example.Third | third"
            )
        );
        BackendFileLogQueryService service = new BackendFileLogQueryService(tempDir.toString(), "backend", 1000);

        BackendFileLogResponse response = service.getLogsByTimeWindow(
            Instant.parse("2026-08-29T19:03:00Z"),
            Instant.parse("2026-08-29T19:08:00Z"),
            "Second",
            "ERROR",
            "pump",
            null
        );

        assertEquals(1, response.returnedCount());
        assertEquals("com.example.Second", response.logs().get(0).sourceClass());
        assertEquals("ERROR", response.logs().get(0).logLevel());
        assertEquals("pumping", response.logs().get(0).category());
        assertEquals("second", response.logs().get(0).message());
    }

    @Test
    void getLastLogsReturnsTailAcrossMultipleFiles() throws Exception {
        writeLogFile(
            "backend-20260829-1.log",
            List.of(
                "2026-08-29T20:00:00.000+01:00 | INFO | feeding | com.example.First | first",
                "2026-08-29T20:01:00.000+01:00 | DEBUG | feeding | com.example.Second | second"
            )
        );
        writeLogFile(
            "backend-20260829-2.log",
            List.of("2026-08-29T20:02:00.000+01:00 | ERROR | pumping | com.example.Third | third")
        );
        BackendFileLogQueryService service = new BackendFileLogQueryService(tempDir.toString(), "backend", 1000);

        BackendFileLogResponse response = service.getLastLogs(2, null, null, "feed");

        assertEquals(2, response.returnedCount());
        assertEquals("first", response.logs().get(0).message());
        assertEquals("second", response.logs().get(1).message());
    }

    @Test
    void getStorageUsageReportsCurrentLogFootprint() throws Exception {
        writeLogFile("backend-20260829-1.log", List.of("line-one"));
        writeLogFile("backend-20260829-2.log", List.of("line-two", "line-three"));
        BackendFileLogQueryService service = new BackendFileLogQueryService(tempDir.toString(), "backend", 1000);

        BackendFileLogStorageResponse response = service.getStorageUsage();

        assertEquals(2, response.fileCount());
        assertTrue(response.totalBytes() > 0);
        assertFalse(response.totalSizeDisplay().isBlank());
    }

    @Test
    void parsesLegacyThreeFieldLogFormatWithDefaultLevelAndCategory() throws Exception {
        writeLogFile(
            "backend-20260829-1.log",
            List.of("2026-08-29T20:00:00.000+01:00 | com.example.LegacyClass | legacy message")
        );
        BackendFileLogQueryService service = new BackendFileLogQueryService(tempDir.toString(), "backend", 1000);

        BackendFileLogResponse response = service.getLastLogs(10, null, null, null);

        assertEquals(1, response.returnedCount());
        assertEquals("INFO", response.logs().get(0).logLevel());
        assertEquals("general", response.logs().get(0).category());
        assertEquals("legacy message", response.logs().get(0).message());
    }

    @Test
    void getLastLogsCapsResultsToConfiguredMaximum() throws Exception {
        writeLogFile(
            "backend-20260829-1.log",
            List.of(
                "2026-08-29T20:00:00.000+01:00 | INFO | feeding | com.example.First | first",
                "2026-08-29T20:01:00.000+01:00 | INFO | feeding | com.example.Second | second",
                "2026-08-29T20:02:00.000+01:00 | INFO | feeding | com.example.Third | third"
            )
        );
        BackendFileLogQueryService service = new BackendFileLogQueryService(tempDir.toString(), "backend", 2);

        BackendFileLogResponse response = service.getLastLogs(100, null, null, null);

        assertEquals(2, response.returnedCount());
        assertEquals("second", response.logs().get(0).message());
        assertEquals("third", response.logs().get(1).message());
    }

    @Test
    void getLogsByTimeWindowCapsToRequestedLimit() throws Exception {
        writeLogFile(
            "backend-20260829-1.log",
            List.of(
                "2026-08-29T20:00:00.000+01:00 | INFO | feeding | com.example.First | first",
                "2026-08-29T20:01:00.000+01:00 | INFO | feeding | com.example.Second | second",
                "2026-08-29T20:02:00.000+01:00 | INFO | feeding | com.example.Third | third"
            )
        );
        BackendFileLogQueryService service = new BackendFileLogQueryService(tempDir.toString(), "backend", 1000);

        BackendFileLogResponse response = service.getLogsByTimeWindow(
            Instant.parse("2026-08-29T18:00:00Z"),
            Instant.parse("2026-08-29T22:00:00Z"),
            null,
            null,
            null,
            2
        );

        assertEquals(2, response.returnedCount());
        assertEquals("second", response.logs().get(0).message());
        assertEquals("third", response.logs().get(1).message());
    }

    private void writeLogFile(String fileName, List<String> lines) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, String.join(System.lineSeparator(), lines), StandardCharsets.UTF_8);
    }
}
