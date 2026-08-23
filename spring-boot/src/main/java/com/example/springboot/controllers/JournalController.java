package com.example.springboot.controllers;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.PathVariable;

import com.example.springboot.models.JournalBackupBootstrapPollResponse;
import com.example.springboot.models.JournalBackupBootstrapStartRequest;
import com.example.springboot.models.JournalBackupBootstrapStartResponse;
import com.example.springboot.models.JournalBackupHealthStatus;
import com.example.springboot.models.JournalBackupStatus;
import com.example.springboot.models.JournalDayEntry;
import com.example.springboot.models.JournalEntrySaveRequest;
import com.example.springboot.models.JournalImageUploadResponse;
import com.example.springboot.models.JournalMonthIndexResponse;
import com.example.springboot.models.JournalPdfExportJobStartResponse;
import com.example.springboot.models.JournalPdfExportJobStatusResponse;
import com.example.springboot.models.JournalPdfExportResult;
import com.example.springboot.services.JournalOneDriveBackupService;
import com.example.springboot.services.JournalPdfExportJobService;
import com.example.springboot.services.JournalPdfExportService;
import com.example.springboot.services.JournalService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
public class JournalController {

    private final JournalService journalService;
    private final JournalPdfExportService journalPdfExportService;
    private final JournalPdfExportJobService journalPdfExportJobService;
    private final JournalOneDriveBackupService backupService;

    @GetMapping("/entry")
    public JournalDayEntry getEntry(@RequestParam LocalDate date) {
        return journalService.getDayEntry(date);
    }

    @PutMapping("/entry")
    public JournalDayEntry saveEntry(@RequestParam LocalDate date, @RequestBody JournalEntrySaveRequest request) {
        boolean createdNewEntry = !journalService.hasDayEntry(date);
        JournalDayEntry savedEntry = journalService.saveDayEntry(date, request.markdown());

        if (createdNewEntry) {
            backupService.triggerBackupAsync();
        }

        return savedEntry;
    }

    @GetMapping("/month")
    public JournalMonthIndexResponse getMonthIndex(@RequestParam int year, @RequestParam int month) {
        return journalService.getMonthIndex(year, month);
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JournalImageUploadResponse uploadImage(@RequestParam LocalDate date, @RequestParam("image") MultipartFile image) {
        return journalService.uploadImage(date, image);
    }

    @GetMapping(value = "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to
    ) {
        JournalPdfExportResult exportResult = journalPdfExportService.exportAsPdf(from, to);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportResult.fileName() + "\"")
            .body(exportResult.content());
    }

    @PostMapping("/export/pdf/jobs")
    public ResponseEntity<JournalPdfExportJobStartResponse> startPdfExportJob(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to
    ) {
        JournalPdfExportJobStartResponse startResponse = journalPdfExportJobService.startExportJob(from, to);
        return ResponseEntity.accepted().body(startResponse);
    }

    @GetMapping("/export/pdf/jobs/{jobId}")
    public JournalPdfExportJobStatusResponse getPdfExportJobStatus(@PathVariable String jobId) {
        return journalPdfExportJobService.getJobStatus(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export job not found"));
    }

    @GetMapping(value = "/export/pdf/jobs/{jobId}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdfExportJob(@PathVariable String jobId) {
        JournalPdfExportJobStatusResponse status = journalPdfExportJobService.getJobStatus(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export job not found"));

        if (!status.downloadable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Export job is not complete yet");
        }

        JournalPdfExportResult exportResult = journalPdfExportJobService.loadCompletedExport(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export file not found"));

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportResult.fileName() + "\"")
            .body(exportResult.content());
    }

    @GetMapping("/backup/status")
    public JournalBackupStatus getBackupStatus() {
        return backupService.getStatus();
    }

    @PostMapping("/backup/trigger")
    public JournalBackupStatus triggerBackup() {
        backupService.triggerBackupAsync();
        return backupService.getStatus();
    }

    @GetMapping("/backup/health")
    public JournalBackupHealthStatus getBackupHealth() {
        return backupService.checkConfigurationHealth();
    }

    @PostMapping("/backup/bootstrap/device/start")
    public JournalBackupBootstrapStartResponse startDeviceBootstrap(@RequestBody JournalBackupBootstrapStartRequest request) {
        return backupService.startDeviceCodeBootstrap(request);
    }

    @PostMapping("/backup/bootstrap/device/poll")
    public JournalBackupBootstrapPollResponse pollDeviceBootstrap(@RequestParam String requestId) {
        return backupService.pollDeviceCodeBootstrap(requestId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleInternalError(IllegalStateException ex) {
        return Map.of("error", ex.getMessage());
    }
}
