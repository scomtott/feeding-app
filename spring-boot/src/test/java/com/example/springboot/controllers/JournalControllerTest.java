package com.example.springboot.controllers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.example.springboot.models.JournalPdfExportJobStartResponse;
import com.example.springboot.models.JournalPdfExportJobStatusResponse;
import com.example.springboot.models.JournalPdfExportResult;
import com.example.springboot.services.JournalOneDriveBackupService;
import com.example.springboot.services.JournalPdfExportJobService;
import com.example.springboot.services.JournalPdfExportService;
import com.example.springboot.services.JournalService;

class JournalControllerTest {

    @Test
    void exportPdfReturnsAttachmentResponse() {
        JournalService journalService = mock(JournalService.class);
        JournalPdfExportService pdfExportService = mock(JournalPdfExportService.class);
        JournalPdfExportJobService pdfExportJobService = mock(JournalPdfExportJobService.class);
        JournalOneDriveBackupService backupService = mock(JournalOneDriveBackupService.class);
        JournalController controller = new JournalController(journalService, pdfExportService, pdfExportJobService, backupService);

        byte[] payload = new byte[] {1, 2, 3, 4};
        when(pdfExportService.exportAsPdf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
            .thenReturn(new JournalPdfExportResult("journal-2026-01-01-to-2026-01-31.pdf", payload));

        ResponseEntity<byte[]> response = controller.exportPdf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals(
            "attachment; filename=\"journal-2026-01-01-to-2026-01-31.pdf\"",
            response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
        );
        assertArrayEquals(payload, response.getBody());
    }

    @Test
    void startPdfExportJobReturnsAccepted() {
        JournalController controller = createController();
        JournalPdfExportJobStartResponse jobStartResponse = new JournalPdfExportJobStartResponse(
            "job-123",
            "PENDING",
            Instant.parse("2026-08-18T21:00:00Z"),
            "Export job queued"
        );
        when(controllerDeps.jobService.startExportJob(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
            .thenReturn(jobStartResponse);

        ResponseEntity<JournalPdfExportJobStartResponse> response =
            controller.startPdfExportJob(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(jobStartResponse, response.getBody());
    }

    @Test
    void downloadPdfExportJobReturnsAttachmentWhenComplete() {
        JournalController controller = createController();
        JournalPdfExportJobStatusResponse status = new JournalPdfExportJobStatusResponse(
            "job-123",
            "COMPLETED",
            Instant.parse("2026-08-18T21:00:00Z"),
            Instant.parse("2026-08-18T21:00:01Z"),
            Instant.parse("2026-08-18T21:00:10Z"),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 31),
            "journal-2026-01-01-to-2026-01-31.pdf",
            true,
            "Export completed"
        );
        byte[] payload = new byte[] {9, 8, 7};

        when(controllerDeps.jobService.getJobStatus("job-123")).thenReturn(Optional.of(status));
        when(controllerDeps.jobService.loadCompletedExport("job-123"))
            .thenReturn(Optional.of(new JournalPdfExportResult(status.fileName(), payload)));

        ResponseEntity<byte[]> response = controller.downloadPdfExportJob("job-123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals(
            "attachment; filename=\"journal-2026-01-01-to-2026-01-31.pdf\"",
            response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
        );
        assertArrayEquals(payload, response.getBody());
    }

    @Test
    void getPdfExportJobStatusThrowsNotFoundWhenMissing() {
        JournalController controller = createController();
        when(controllerDeps.jobService.getJobStatus("missing")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> controller.getPdfExportJobStatus("missing")
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private ControllerDeps controllerDeps;

    private JournalController createController() {
        JournalService journalService = mock(JournalService.class);
        JournalPdfExportService pdfExportService = mock(JournalPdfExportService.class);
        JournalPdfExportJobService pdfExportJobService = mock(JournalPdfExportJobService.class);
        JournalOneDriveBackupService backupService = mock(JournalOneDriveBackupService.class);
        this.controllerDeps = new ControllerDeps(journalService, pdfExportService, pdfExportJobService, backupService);
        return new JournalController(journalService, pdfExportService, pdfExportJobService, backupService);
    }

    private record ControllerDeps(
        JournalService journalService,
        JournalPdfExportService pdfService,
        JournalPdfExportJobService jobService,
        JournalOneDriveBackupService backupService
    ) { }
}
