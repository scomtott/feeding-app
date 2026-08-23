package com.example.springboot.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.springboot.models.JournalPdfExportJobStartResponse;
import com.example.springboot.models.JournalPdfExportJobStatusResponse;
import com.example.springboot.models.JournalPdfExportResult;

class JournalPdfExportJobServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void startExportJobCompletesAndBecomesDownloadable() throws Exception {
        JournalPdfExportService pdfExportService = mock(JournalPdfExportService.class);
        when(pdfExportService.exportAsPdf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
            .thenReturn(new JournalPdfExportResult("journal-2026-01-01-to-2026-01-31.pdf", "pdf-data".getBytes()));

        JournalPdfExportJobService jobService = new JournalPdfExportJobService(pdfExportService, tempDir.toString());
        JournalPdfExportJobStartResponse startResponse =
            jobService.startExportJob(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        JournalPdfExportJobStatusResponse completedStatus = waitForTerminalState(jobService, startResponse.jobId());
        assertEquals("COMPLETED", completedStatus.state());
        assertTrue(completedStatus.downloadable());
        assertEquals("journal-2026-01-01-to-2026-01-31.pdf", completedStatus.fileName());

        JournalPdfExportResult exportResult = jobService.loadCompletedExport(startResponse.jobId()).orElseThrow();
        assertArrayEquals("pdf-data".getBytes(), exportResult.content());
    }

    @Test
    void getJobStatusReturnsEmptyForUnknownJob() {
        JournalPdfExportService pdfExportService = mock(JournalPdfExportService.class);
        JournalPdfExportJobService jobService = new JournalPdfExportJobService(pdfExportService, tempDir.toString());

        Optional<JournalPdfExportJobStatusResponse> status = jobService.getJobStatus("missing-job");

        assertTrue(status.isEmpty());
        assertFalse(jobService.loadCompletedExport("missing-job").isPresent());
    }

    @Test
    void startExportJobRejectsInvertedRange() {
        JournalPdfExportService pdfExportService = mock(JournalPdfExportService.class);
        JournalPdfExportJobService jobService = new JournalPdfExportJobService(pdfExportService, tempDir.toString());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> jobService.startExportJob(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1))
        );

        assertEquals("'from' date must be before or equal to 'to' date", exception.getMessage());
    }

    private JournalPdfExportJobStatusResponse waitForTerminalState(JournalPdfExportJobService jobService, String jobId)
        throws InterruptedException {
        long timeoutMillis = 5_000;
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeoutMillis) {
            JournalPdfExportJobStatusResponse status = jobService.getJobStatus(jobId).orElseThrow();
            if ("COMPLETED".equals(status.state()) || "FAILED".equals(status.state())) {
                return status;
            }
            Thread.sleep(20);
        }

        throw new IllegalStateException("Timed out waiting for async export job completion");
    }
}
