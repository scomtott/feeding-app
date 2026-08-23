package com.example.springboot.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.example.springboot.models.JournalImageUploadResponse;

class JournalServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadImageStoresVideoWithVideoExtensionWhenOriginalNameHasNoExtension() throws Exception {
        JournalService service = createService();
        LocalDate date = LocalDate.of(2026, 8, 18);
        MockMultipartFile video = new MockMultipartFile(
            "image",
            "blob",
            "video/mp4",
            "fake-video-content".getBytes()
        );

        JournalImageUploadResponse response = service.uploadImage(date, video);

        assertTrue(response.fileName().endsWith(".mp4"));
        assertEquals("![](" + response.url() + ")", response.markdown());
        Path storedFile = tempDir
            .resolve("images")
            .resolve("2026")
            .resolve("08")
            .resolve("18")
            .resolve(response.fileName());
        assertTrue(Files.exists(storedFile));
    }

    @Test
    void uploadImageRejectsUnsupportedMediaType() {
        JournalService service = createService();
        MockMultipartFile unsupportedFile = new MockMultipartFile(
            "image",
            "notes.txt",
            "text/plain",
            "hello".getBytes()
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.uploadImage(LocalDate.of(2026, 8, 18), unsupportedFile)
        );
        assertEquals("Unsupported media type", exception.getMessage());
    }

    private JournalService createService() {
        return new JournalService(
            tempDir.resolve("entries").toString(),
            tempDir.resolve("images").toString()
        );
    }
}
