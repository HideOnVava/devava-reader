package com.devavaxp.reader.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the PDF page source against a document generated on the fly with PDFBox:
 * five pages of different sizes (one rotated), each filled with a solid colour, plus a
 * nested outline with a couple of entries that must be ignored.
 */
class PdfPageSourceTest {

    @TempDir
    static Path dir;

    static Path sample;

    private static final Color[] COLORS = {Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA, Color.ORANGE};

    @BeforeAll
    static void writeSample() throws IOException {
        sample = dir.resolve("sample.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("  Sample Manga  ");
            doc.setDocumentInformation(info);

            PDRectangle portrait = new PDRectangle(400, 600);
            PDRectangle wide = new PDRectangle(800, 500);
            PDRectangle[] boxes = {portrait, portrait, portrait, wide, portrait};
            for (int i = 0; i < boxes.length; i++) {
                PDPage page = new PDPage(boxes[i]);
                if (i == 2) page.setRotation(90); // a portrait page meant to be viewed sideways
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.setNonStrokingColor(COLORS[i]);
                    cs.addRect(0, 0, boxes[i].getWidth(), boxes[i].getHeight());
                    cs.fill();
                }
            }

            PDDocumentOutline outline = new PDDocumentOutline();
            doc.getDocumentCatalog().setDocumentOutline(outline);
            outline.addLast(chapter(doc, "Chapter 1", 0));
            PDOutlineItem second = chapter(doc, "Chapter 2", 2);
            second.addLast(chapter(doc, "Extra pages", 3));
            outline.addLast(second);
            outline.addLast(chapter(doc, "   ", 4));          // blank title: ignored
            PDOutlineItem dangling = new PDOutlineItem();       // no destination: ignored
            dangling.setTitle("Nowhere");
            outline.addLast(dangling);
            outline.addLast(chapter(doc, "Chapter 3", 4));

            doc.save(sample.toFile());
        }
    }

    private static PDOutlineItem chapter(PDDocument doc, String title, int page) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        item.setDestination(doc.getPage(page));
        return item;
    }

    @Test
    void readsMetadataAndPageSizes() throws IOException {
        try (PdfPageSource source = PdfPageSource.open(sample)) {
            assertEquals("Sample Manga", source.title(), "title is trimmed");
            assertEquals(5, source.pageCount());
            assertEquals(new PageSource.PageSize(400, 600), source.pageSize(0));
            assertEquals(new PageSource.PageSize(600, 400), source.pageSize(2), "rotation swaps width and height");
            assertEquals(new PageSource.PageSize(800, 500), source.pageSize(3));
            assertEquals(source.pageSize(0), source.pageSize(-1), "indices are clamped");
            assertEquals(source.pageSize(4), source.pageSize(99));
            assertEquals(400.0 / 600.0, source.pageSize(0).aspect(), 1e-9);
        }
    }

    @Test
    void rendersPagesAtTheRequestedScale() throws IOException {
        try (PdfPageSource source = PdfPageSource.open(sample)) {
            BufferedImage full = source.render(0, 1.0);
            assertEquals(400, full.getWidth());
            assertEquals(600, full.getHeight());
            assertEquals(Color.RED.getRGB() & 0xFFFFFF, full.getRGB(200, 300) & 0xFFFFFF);

            BufferedImage half = source.render(1, 0.5);
            assertEquals(200, half.getWidth());
            assertEquals(300, half.getHeight());
            assertEquals(Color.GREEN.getRGB() & 0xFFFFFF, half.getRGB(100, 150) & 0xFFFFFF);

            BufferedImage rotated = source.render(2, 1.0);
            assertEquals(600, rotated.getWidth(), "the rotated page is rendered sideways");
            assertEquals(400, rotated.getHeight());
            assertEquals(Color.BLUE.getRGB() & 0xFFFFFF, rotated.getRGB(300, 200) & 0xFFFFFF);

            BufferedImage tiny = source.render(3, 0.0001);
            assertEquals(40, tiny.getWidth(), "the scale has a sane lower bound");
            assertEquals(25, tiny.getHeight());

            BufferedImage last = source.render(99, 0.25);
            assertEquals(100, last.getWidth(), "out-of-range indices render the last page");
            assertEquals(Color.ORANGE.getRGB() & 0xFFFFFF, last.getRGB(50, 75) & 0xFFFFFF);
        }
    }

    @Test
    void readsTheOutlineWithLevels() throws IOException {
        try (PdfPageSource source = PdfPageSource.open(sample)) {
            List<PageSource.OutlineEntry> outline = source.outline();
            assertEquals(List.of(
                    new PageSource.OutlineEntry("Chapter 1", 0, 0),
                    new PageSource.OutlineEntry("Chapter 2", 2, 0),
                    new PageSource.OutlineEntry("Extra pages", 3, 1),
                    new PageSource.OutlineEntry("Chapter 3", 4, 0)), outline);
        }
    }

    @Test
    void rejectsUnreadableFiles() throws IOException {
        assertThrows(IOException.class, () -> PdfPageSource.open(dir.resolve("missing.pdf")));

        Path garbage = dir.resolve("garbage.pdf");
        Files.writeString(garbage, "%PDF-1.7\nthis is not a pdf at all", StandardCharsets.US_ASCII);
        assertThrows(IOException.class, () -> PdfPageSource.open(garbage));

        Path locked = dir.resolve("locked.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A5));
            doc.protect(new StandardProtectionPolicy("owner", "user", new AccessPermission()));
            doc.save(locked.toFile());
        }
        assertThrows(IOException.class, () -> PdfPageSource.open(locked), "password-protected files are refused");
    }

    @Test
    void canBeClosedMoreThanOnce() throws IOException {
        PdfPageSource source = PdfPageSource.open(sample);
        assertTrue(source.pageCount() > 0);
        source.close();
        source.close();
    }
}
