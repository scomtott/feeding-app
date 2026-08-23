package com.example.springboot.services;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.springboot.models.JournalDayEntry;
import com.example.springboot.models.JournalPdfExportResult;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class JournalPdfExportService {

    private static final Pattern IMAGE_MARKDOWN_PATTERN = Pattern.compile(
        "!\\[[^\\]]*]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)"
    );
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".webm", ".ogg", ".ogv", ".mov", ".m4v");
    private static final PDFont TITLE_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont HEADER_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont BODY_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final float TITLE_FONT_SIZE = 20f;
    private static final float HEADER_FONT_SIZE = 14f;
    private static final float BODY_FONT_SIZE = 11f;
    private static final float TOP_MARGIN = 52f;
    private static final float BOTTOM_MARGIN = 52f;
    private static final float SIDE_MARGIN = 52f;
    private static final float LINE_GAP = 4f;
    private static final float SECTION_GAP = 12f;
    private static final float IMAGE_GAP = 12f;
    private static final float IMAGE_GRID_ROW_HEIGHT = 180f;
    private static final float MAX_WIDE_IMAGE_HEIGHT = 260f;
    private static final float MIN_IMAGE_QUALITY = 0.1f;
    private static final float MAX_IMAGE_QUALITY = 1.0f;
    private static final int MIN_IMAGE_DPI = 72;
    private static final int MAX_IMAGE_DPI = 300;

    private final JournalService journalService;
    private final Path imageRoot;
    private final float embeddedImageQuality;
    private final int embeddedImageDpi;

    public JournalPdfExportService(
        JournalService journalService,
        @Value("${journal.storage.image-root:./data/journal/images}") String imageRoot,
        @Value("${journal.export.image-quality:0.7}") float embeddedImageQuality,
        @Value("${journal.export.image-dpi:130}") int embeddedImageDpi
    ) {
        this.journalService = journalService;
        this.imageRoot = Paths.get(imageRoot).toAbsolutePath().normalize();
        this.embeddedImageQuality = normalizeImageQuality(embeddedImageQuality);
        this.embeddedImageDpi = normalizeImageDpi(embeddedImageDpi);
    }

    public JournalPdfExportResult exportAsPdf(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' date must be before or equal to 'to' date");
        }

        List<LocalDate> allDates = journalService.listEntryDates();
        DateRange effectiveRange = resolveEffectiveRange(allDates, from, to);
        List<LocalDate> datesToExport = filterDates(allDates, effectiveRange.start(), effectiveRange.end());

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            try (PdfCanvas canvas = new PdfCanvas(document)) {
                canvas.writeLine("Journal Export", TITLE_FONT, TITLE_FONT_SIZE);
                canvas.addSpacing(SECTION_GAP);

                if (datesToExport.isEmpty()) {
                    canvas.writeWrappedText("No journal entries found for the selected range.", BODY_FONT, BODY_FONT_SIZE);
                } else {
                    for (LocalDate date : datesToExport) {
                        JournalDayEntry entry = journalService.getDayEntry(date);
                        renderEntry(canvas, entry);
                    }
                }
            }

            document.save(output);
            String fileName = buildFileName(from, to, effectiveRange.start(), effectiveRange.end());
            return new JournalPdfExportResult(fileName, output.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate journal PDF", ex);
        }
    }

    List<String> extractImageUrlsForPdf(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        List<String> imageUrls = new ArrayList<>();
        Matcher matcher = IMAGE_MARKDOWN_PATTERN.matcher(markdown);
        while (matcher.find()) {
            String url = normalizeMarkdownUrl(matcher.group(1));
            if (!url.isBlank() && !isVideoUrl(url)) {
                imageUrls.add(url);
            }
        }
        return imageUrls;
    }

    private void renderEntry(PdfCanvas canvas, JournalDayEntry entry) throws IOException {
        canvas.addSpacing(SECTION_GAP);
        canvas.writeLine(entry.date().toString(), HEADER_FONT, HEADER_FONT_SIZE);

        String markdown = entry.markdown() == null ? "" : entry.markdown();
        String textContent = stripImageMarkdown(markdown).trim();
        if (!textContent.isBlank()) {
            canvas.writeWrappedText(textContent, BODY_FONT, BODY_FONT_SIZE);
        }

        List<Path> imagePaths = resolveImagePaths(extractImageUrlsForPdf(markdown));
        if (!imagePaths.isEmpty()) {
            canvas.addSpacing(6f);
            renderImages(canvas, imagePaths);
        }
    }

    private List<Path> resolveImagePaths(List<String> imageUrls) {
        List<Path> imagePaths = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            Path resolvedPath = resolveJournalMediaPath(imageUrl);
            if (resolvedPath == null || isVideoUrl(imageUrl)) {
                continue;
            }
            if (!Files.exists(resolvedPath)) {
                log.warn("Skipping missing journal image during PDF export: {}", resolvedPath);
                continue;
            }
            imagePaths.add(resolvedPath);
        }
        return imagePaths;
    }

    private void renderImages(PdfCanvas canvas, List<Path> imagePaths) throws IOException {
        float fullWidth = canvas.getUsableWidth();
        float cellWidth = (fullWidth - IMAGE_GAP) / 2f;
        int columnIndex = 0;

        for (Path imagePath : imagePaths) {
            RenderedImage renderedImage = loadImage(canvas.document(), imagePath, cellWidth, fullWidth);
            if (renderedImage == null) {
                continue;
            }

            if (renderedImage.wide()) {
                if (columnIndex == 1) {
                    canvas.consumeImageGridRow(IMAGE_GRID_ROW_HEIGHT + IMAGE_GAP);
                    columnIndex = 0;
                }

                canvas.ensureSpace(renderedImage.height() + IMAGE_GAP);
                float drawY = canvas.cursorY() - renderedImage.height();
                canvas.drawImage(renderedImage.image(), SIDE_MARGIN, drawY, renderedImage.width(), renderedImage.height());
                canvas.setCursorY(drawY - IMAGE_GAP);
                continue;
            }

            if (columnIndex == 0) {
                canvas.ensureSpace(IMAGE_GRID_ROW_HEIGHT + IMAGE_GAP);
            }

            float baseX = SIDE_MARGIN + (columnIndex * (cellWidth + IMAGE_GAP));
            float drawX = baseX + ((cellWidth - renderedImage.width()) / 2f);
            float drawY = canvas.cursorY() - renderedImage.height();
            canvas.drawImage(renderedImage.image(), drawX, drawY, renderedImage.width(), renderedImage.height());

            if (columnIndex == 0) {
                columnIndex = 1;
            } else {
                canvas.consumeImageGridRow(IMAGE_GRID_ROW_HEIGHT + IMAGE_GAP);
                columnIndex = 0;
            }
        }

        if (columnIndex == 1) {
            canvas.consumeImageGridRow(IMAGE_GRID_ROW_HEIGHT + IMAGE_GAP);
        }
    }

    private RenderedImage loadImage(PDDocument document, Path imagePath, float cellWidth, float fullWidth) {
        try {
            BufferedImage bufferedImage = ImageIO.read(imagePath.toFile());
            if (bufferedImage == null) {
                log.warn("Skipping unsupported journal image during PDF export: {}", imagePath);
                return null;
            }

            float originalWidth = bufferedImage.getWidth();
            float originalHeight = bufferedImage.getHeight();
            if (originalWidth <= 0 || originalHeight <= 0) {
                return null;
            }

            float aspectRatio = originalWidth / originalHeight;
            boolean isWide = aspectRatio >= 1.45f;
            float targetWidth = isWide ? fullWidth : cellWidth;
            float targetHeight = targetWidth / aspectRatio;

            float maxHeight = isWide ? MAX_WIDE_IMAGE_HEIGHT : IMAGE_GRID_ROW_HEIGHT;
            if (targetHeight > maxHeight) {
                float scale = maxHeight / targetHeight;
                targetHeight = maxHeight;
                targetWidth = targetWidth * scale;
            }

            BufferedImage compressedImage = toCompressedPdfImage(bufferedImage, targetWidth, targetHeight);
            PDImageXObject pdfImage = JPEGFactory.createFromImage(document, compressedImage, embeddedImageQuality);
            return new RenderedImage(pdfImage, targetWidth, targetHeight, isWide);
        } catch (IOException ex) {
            log.warn("Skipping unreadable journal image during PDF export: {}", imagePath, ex);
            return null;
        }
    }

    private DateRange resolveEffectiveRange(List<LocalDate> allDates, LocalDate from, LocalDate to) {
        if (allDates.isEmpty()) {
            return new DateRange(from, to);
        }

        LocalDate earliest = allDates.get(0);
        LocalDate latest = allDates.get(allDates.size() - 1);
        LocalDate effectiveFrom = from != null ? from : earliest;
        LocalDate effectiveTo = to != null ? to : latest;

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("'from' date must be before or equal to 'to' date");
        }
        return new DateRange(effectiveFrom, effectiveTo);
    }

    private List<LocalDate> filterDates(List<LocalDate> dates, LocalDate start, LocalDate end) {
        if (dates.isEmpty()) {
            return List.of();
        }

        LocalDate effectiveStart = start != null ? start : dates.get(0);
        LocalDate effectiveEnd = end != null ? end : dates.get(dates.size() - 1);

        List<LocalDate> result = new ArrayList<>();
        for (LocalDate date : dates) {
            if ((date.isEqual(effectiveStart) || date.isAfter(effectiveStart))
                && (date.isEqual(effectiveEnd) || date.isBefore(effectiveEnd))) {
                result.add(date);
            }
        }
        return result;
    }

    private String stripImageMarkdown(String markdown) {
        return IMAGE_MARKDOWN_PATTERN.matcher(markdown).replaceAll("");
    }

    private String normalizeMarkdownUrl(String url) {
        if (url == null) {
            return "";
        }

        String normalized = url.trim();
        if (normalized.startsWith("<") && normalized.endsWith(">") && normalized.length() > 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private Path resolveJournalMediaPath(String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) {
            return null;
        }

        String cleanUrl = mediaUrl.split("#", 2)[0].split("\\?", 2)[0];
        String relativePath;
        if (cleanUrl.startsWith("/journal-media/")) {
            relativePath = cleanUrl.substring("/journal-media/".length());
        } else if (cleanUrl.startsWith("journal-media/")) {
            relativePath = cleanUrl.substring("journal-media/".length());
        } else {
            return null;
        }

        Path resolved = imageRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(imageRoot)) {
            return null;
        }
        return resolved;
    }

    private boolean isVideoUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        String normalized = url.split("#", 2)[0].split("\\?", 2)[0].toLowerCase(Locale.ROOT);
        for (String extension : VIDEO_EXTENSIONS) {
            if (normalized.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private String buildFileName(LocalDate requestedFrom, LocalDate requestedTo, LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (requestedFrom == null && requestedTo == null) {
            return "journal-all.pdf";
        }
        if (effectiveFrom != null && effectiveTo != null) {
            return "journal-" + effectiveFrom + "-to-" + effectiveTo + ".pdf";
        }
        if (requestedFrom != null) {
            return "journal-" + requestedFrom + "-to-latest.pdf";
        }
        if (requestedTo != null) {
            return "journal-earliest-to-" + requestedTo + ".pdf";
        }
        return "journal-export.pdf";
    }

    private BufferedImage toCompressedPdfImage(BufferedImage sourceImage, float targetWidthPoints, float targetHeightPoints) {
        int maxTargetWidthPx = Math.max(1, Math.round(targetWidthPoints * embeddedImageDpi / 72f));
        int maxTargetHeightPx = Math.max(1, Math.round(targetHeightPoints * embeddedImageDpi / 72f));

        int sourceWidth = sourceImage.getWidth();
        int sourceHeight = sourceImage.getHeight();

        float scaleRatio = Math.min(
            1f,
            Math.min(
                maxTargetWidthPx / (float) sourceWidth,
                maxTargetHeightPx / (float) sourceHeight
            )
        );

        int targetWidthPx = Math.max(1, Math.round(sourceWidth * scaleRatio));
        int targetHeightPx = Math.max(1, Math.round(sourceHeight * scaleRatio));

        BufferedImage rgbImage = new BufferedImage(targetWidthPx, targetHeightPx, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgbImage.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidthPx, targetHeightPx);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(sourceImage, 0, 0, targetWidthPx, targetHeightPx, null);
        } finally {
            graphics.dispose();
        }

        return rgbImage;
    }

    private float normalizeImageQuality(float quality) {
        if (Float.isNaN(quality) || Float.isInfinite(quality)) {
            return 0.7f;
        }
        if (quality < MIN_IMAGE_QUALITY) {
            return MIN_IMAGE_QUALITY;
        }
        if (quality > MAX_IMAGE_QUALITY) {
            return MAX_IMAGE_QUALITY;
        }
        return quality;
    }

    private int normalizeImageDpi(int dpi) {
        if (dpi < MIN_IMAGE_DPI) {
            return MIN_IMAGE_DPI;
        }
        if (dpi > MAX_IMAGE_DPI) {
            return MAX_IMAGE_DPI;
        }
        return dpi;
    }

    private static String sanitizeForPdf(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder sanitized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\t') {
                sanitized.append(' ');
                continue;
            }
            if (current < 32 || current > 255) {
                sanitized.append('?');
                continue;
            }
            sanitized.append(current);
        }
        return sanitized.toString();
    }

    private static List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> wrapped = new ArrayList<>();
        String[] paragraphs = text.split("\\R");
        for (String paragraph : paragraphs) {
            String normalized = sanitizeForPdf(paragraph).trim();
            if (normalized.isEmpty()) {
                wrapped.add("");
                continue;
            }

            StringBuilder line = new StringBuilder();
            for (String word : normalized.split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (lineFits(candidate, font, fontSize, maxWidth)) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }

                if (!line.isEmpty()) {
                    wrapped.add(line.toString());
                    line.setLength(0);
                }

                if (lineFits(word, font, fontSize, maxWidth)) {
                    line.append(word);
                    continue;
                }

                List<String> chunks = splitLongWord(word, font, fontSize, maxWidth);
                for (int i = 0; i < chunks.size(); i++) {
                    String chunk = chunks.get(i);
                    if (i == chunks.size() - 1) {
                        line.append(chunk);
                    } else {
                        wrapped.add(chunk);
                    }
                }
            }

            if (!line.isEmpty()) {
                wrapped.add(line.toString());
            }
        }
        return wrapped;
    }

    private static List<String> splitLongWord(String word, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            String candidate = current.toString() + ch;
            if (lineFits(candidate, font, fontSize, maxWidth)) {
                current.append(ch);
                continue;
            }

            if (!current.isEmpty()) {
                parts.add(current.toString());
                current.setLength(0);
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static boolean lineFits(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        if (text.isEmpty()) {
            return true;
        }
        float textWidth = (font.getStringWidth(text) / 1000f) * fontSize;
        return textWidth <= maxWidth;
    }

    private record DateRange(LocalDate start, LocalDate end) { }

    private record RenderedImage(PDImageXObject image, float width, float height, boolean wide) { }

    private static final class PdfCanvas implements AutoCloseable {

        private final PDDocument document;
        private PDPage page;
        private PDPageContentStream contentStream;
        private float cursorY;

        private PdfCanvas(PDDocument document) throws IOException {
            this.document = document;
            addPage();
        }

        private PDDocument document() {
            return document;
        }

        private float getUsableWidth() {
            return page.getMediaBox().getWidth() - (2f * SIDE_MARGIN);
        }

        private float cursorY() {
            return cursorY;
        }

        private void setCursorY(float cursorY) {
            this.cursorY = cursorY;
        }

        private void addPage() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            contentStream = new PDPageContentStream(document, page);
            cursorY = page.getMediaBox().getHeight() - TOP_MARGIN;
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (cursorY - requiredHeight < BOTTOM_MARGIN) {
                addPage();
            }
        }

        private void addSpacing(float spacing) throws IOException {
            ensureSpace(spacing);
            cursorY -= spacing;
        }

        private void writeLine(String text, PDFont font, float fontSize) throws IOException {
            ensureSpace(fontSize + LINE_GAP);
            String sanitized = sanitizeForPdf(text);
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(SIDE_MARGIN, cursorY);
            contentStream.showText(sanitized);
            contentStream.endText();
            cursorY -= fontSize + LINE_GAP;
        }

        private void writeWrappedText(String text, PDFont font, float fontSize) throws IOException {
            List<String> lines = wrapText(text, font, fontSize, getUsableWidth());
            for (String line : lines) {
                if (line.isBlank()) {
                    addSpacing(fontSize / 2f);
                    continue;
                }
                writeLine(line, font, fontSize);
            }
        }

        private void drawImage(PDImageXObject image, float x, float y, float width, float height) throws IOException {
            contentStream.drawImage(image, x, y, width, height);
        }

        private void consumeImageGridRow(float rowHeight) throws IOException {
            ensureSpace(rowHeight);
            cursorY -= rowHeight;
        }

        @Override
        public void close() throws IOException {
            if (contentStream != null) {
                contentStream.close();
            }
        }
    }
}
