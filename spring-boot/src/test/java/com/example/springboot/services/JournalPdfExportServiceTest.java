package com.example.springboot.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.springboot.models.JournalPdfExportResult;

class JournalPdfExportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void exportAsPdfWithoutDatesIncludesAllEntries() throws Exception {
        TestContext context = createContext();
        context.journalService().saveDayEntry(LocalDate.of(2026, 1, 1), "Breakfast note");
        context.journalService().saveDayEntry(LocalDate.of(2026, 1, 2), "Lunch note");
        context.journalService().saveDayEntry(LocalDate.of(2026, 1, 3), "Dinner note");

        JournalPdfExportResult result = context.pdfExportService().exportAsPdf(null, null);
        String pdfText = extractPdfText(result.content());

        assertEquals("journal-all.pdf", result.fileName());
        assertTrue(pdfText.contains("2026-01-01"));
        assertTrue(pdfText.contains("2026-01-02"));
        assertTrue(pdfText.contains("2026-01-03"));
    }

    @Test
    void exportAsPdfSupportsOpenEndedDateRanges() throws Exception {
        TestContext context = createContext();
        context.journalService().saveDayEntry(LocalDate.of(2026, 1, 1), "Day one");
        context.journalService().saveDayEntry(LocalDate.of(2026, 1, 2), "Day two");
        context.journalService().saveDayEntry(LocalDate.of(2026, 1, 3), "Day three");

        JournalPdfExportResult fromOnly = context.pdfExportService().exportAsPdf(LocalDate.of(2026, 1, 2), null);
        String fromOnlyText = extractPdfText(fromOnly.content());
        assertEquals("journal-2026-01-02-to-2026-01-03.pdf", fromOnly.fileName());
        assertFalse(fromOnlyText.contains("2026-01-01"));
        assertTrue(fromOnlyText.contains("2026-01-02"));
        assertTrue(fromOnlyText.contains("2026-01-03"));

        JournalPdfExportResult toOnly = context.pdfExportService().exportAsPdf(null, LocalDate.of(2026, 1, 2));
        String toOnlyText = extractPdfText(toOnly.content());
        assertEquals("journal-2026-01-01-to-2026-01-02.pdf", toOnly.fileName());
        assertTrue(toOnlyText.contains("2026-01-01"));
        assertTrue(toOnlyText.contains("2026-01-02"));
        assertFalse(toOnlyText.contains("2026-01-03"));
    }

    @Test
    void extractImageUrlsForPdfExcludesVideoReferences() {
        TestContext context = createContext();
        String markdown = """
            Day summary
            ![](/journal-media/2026/08/18/photo.jpg)
            ![](/journal-media/2026/08/18/clip.mp4)
            ![](/journal-media/2026/08/18/another.png)
            """;

        List<String> urls = context.pdfExportService().extractImageUrlsForPdf(markdown);

        assertEquals(List.of(
            "/journal-media/2026/08/18/photo.jpg",
            "/journal-media/2026/08/18/another.png"
        ), urls);
    }

    private String extractPdfText(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private TestContext createContext() {
        JournalService journalService = new JournalService(
            tempDir.resolve("entries").toString(),
            tempDir.resolve("images").toString()
        );
        JournalPdfExportService pdfExportService = new JournalPdfExportService(
            journalService,
            tempDir.resolve("images").toString(),
            0.7f,
            130
        );
        return new TestContext(journalService, pdfExportService);
    }

    private record TestContext(JournalService journalService, JournalPdfExportService pdfExportService) { }
}
