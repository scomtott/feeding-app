package com.example.springboot.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.springboot.models.LogLevel;

class BufferedBackendFileLoggerTest {

    @TempDir
    Path tempDir;

    @Test
    void flushesWhenBufferSizeThresholdIsReached() throws Exception {
        BufferedBackendFileLogger logger = new BufferedBackendFileLogger(
            tempDir.toString(),
            "backend-test",
            2,
            10 * 1024 * 1024
        );

        logger.log(BufferedBackendFileLoggerTest.class, "first message");
        logger.log(BufferedBackendFileLoggerTest.class, "second message");

        List<String> lines = readAllLogLines(tempDir);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("BufferedBackendFileLoggerTest"));
        assertTrue(lines.get(0).contains(" | INFO | general | "));
        assertTrue(lines.get(0).contains("first message"));
    }

    @Test
    void writesLevelAndCategoryIntoLogFormat() throws Exception {
        BufferedBackendFileLogger logger = new BufferedBackendFileLogger(
            tempDir.toString(),
            "backend-test",
            1,
            10 * 1024 * 1024
        );

        logger.log("com.example.SomeClass", LogLevel.ERROR, "automation", "failure happened");

        List<String> lines = readAllLogLines(tempDir);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains(" | ERROR | automation | com.example.SomeClass | failure happened"));
    }

    @Test
    void flushesLogsWhenTimeWindowHandlerRuns() throws Exception {
        BufferedBackendFileLogger logger = new BufferedBackendFileLogger(
            tempDir.toString(),
            "backend-test",
            100,
            10 * 1024 * 1024
        );

        logger.log("com.example.SomeClass", "scheduled flush message");
        logger.flushIfBufferedLogsExist();

        List<String> lines = readAllLogLines(tempDir);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("com.example.SomeClass"));
        assertTrue(lines.get(0).contains("scheduled flush message"));
    }

    @Test
    void rotatesToNewFileWhenCurrentFileExceedsMaxSize() throws Exception {
        BufferedBackendFileLogger logger = new BufferedBackendFileLogger(
            tempDir.toString(),
            "backend-test",
            1,
            120
        );

        logger.log("com.example.LogSource", "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz");
        logger.log("com.example.LogSource", "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz");
        logger.log("com.example.LogSource", "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz");

        List<Path> files = listLogFiles(tempDir);
        assertTrue(files.size() > 1);
    }

    @Test
    void supportsConcurrentWritesToBuffer7() throws Exception {
        BufferedBackendFileLogger logger = new BufferedBackendFileLogger(
            tempDir.toString(),
            "backend-test",
            1000,
            10 * 1024 * 1024
        );

        int totalLogs = 200;
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalLogs);

        for (int i = 0; i < totalLogs; i++) {
            int logIndex = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    logger.log("com.example.ConcurrentClass", "message-" + logIndex);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        logger.flushIfBufferedLogsExist();

        List<String> lines = readAllLogLines(tempDir);
        assertEquals(totalLogs, lines.size());
        assertFalse(lines.isEmpty());
    }

    private static List<Path> listLogFiles(Path directory) throws Exception {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                .filter(Files::isRegularFile)
                .sorted()
                .toList();
        }
    }

    private static List<String> readAllLogLines(Path directory) throws Exception {
        List<Path> files = listLogFiles(directory);
        try (Stream<String> lines = files.stream().flatMap(BufferedBackendFileLoggerTest::safeReadLines)) {
            return lines.toList();
        }
    }

    private static Stream<String> safeReadLines(Path filePath) {
        try {
            return Files.readAllLines(filePath).stream();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed reading test log file: " + filePath, ex);
        }
    }
}
