package com.devavaxp.reader.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link PageSource} backed by Apache PDFBox.
 * <p>
 * The document is read from the file on demand (nothing is loaded into memory up front,
 * which matters for scanned manga of hundreds of megabytes) and decoded streams are cached
 * in temporary files rather than in the heap.
 */
public final class PdfPageSource implements PageSource {

    private final PDDocument document;
    private final PDFRenderer renderer;
    private final String title;
    private final int pageCount;
    private final List<PageSize> sizes;
    private final List<OutlineEntry> outline;

    private PdfPageSource(PDDocument document) {
        this.document = document;
        this.renderer = new PDFRenderer(document);
        this.renderer.setSubsamplingAllowed(true); // large scans are downsampled while decoding
        this.pageCount = document.getNumberOfPages();
        this.title = readTitle(document);
        this.sizes = readSizes(document);
        this.outline = readOutline(document);
    }

    /** Opens a PDF. Throws for missing, damaged or password-protected files. */
    public static PdfPageSource open(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("File not found: " + file);
        }
        PDDocument document = Loader.loadPDF(file.toFile(), IOUtils.createTempFileOnlyStreamCache());
        if (document.getNumberOfPages() <= 0) {
            document.close();
            throw new IOException("The PDF has no pages.");
        }
        return new PdfPageSource(document);
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public int pageCount() {
        return pageCount;
    }

    @Override
    public PageSize pageSize(int index) {
        return sizes.get(Math.max(0, Math.min(pageCount - 1, index)));
    }

    @Override
    public synchronized BufferedImage render(int index, double pixelsPerUnit) throws IOException {
        int page = Math.max(0, Math.min(pageCount - 1, index));
        float scale = (float) Math.max(0.05, pixelsPerUnit);
        return renderer.renderImage(page, scale, ImageType.RGB);
    }

    @Override
    public List<OutlineEntry> outline() {
        return outline;
    }

    @Override
    public synchronized void close() {
        try {
            document.close();
        } catch (IOException ignored) {
            // Nothing sensible to do while closing.
        }
    }

    // ------------------------------------------------------------------
    // Metadata
    // ------------------------------------------------------------------

    private static String readTitle(PDDocument document) {
        try {
            PDDocumentInformation info = document.getDocumentInformation();
            String t = info == null ? null : info.getTitle();
            return t == null ? "" : t.trim();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Page sizes with the page rotation applied, so width/height match what is rendered. */
    private static List<PageSize> readSizes(PDDocument document) {
        List<PageSize> list = new ArrayList<>(document.getNumberOfPages());
        for (PDPage page : document.getPages()) {
            PDRectangle box = page.getCropBox();
            if (box == null) box = page.getMediaBox();
            double w = box == null ? 595 : Math.max(1, box.getWidth());
            double h = box == null ? 842 : Math.max(1, box.getHeight());
            int rotation = ((page.getRotation() % 360) + 360) % 360;
            boolean sideways = rotation == 90 || rotation == 270;
            list.add(sideways ? new PageSize(h, w) : new PageSize(w, h));
        }
        return List.copyOf(list);
    }

    private static List<OutlineEntry> readOutline(PDDocument document) {
        List<OutlineEntry> entries = new ArrayList<>();
        try {
            PDDocumentOutline root = document.getDocumentCatalog().getDocumentOutline();
            if (root != null) {
                walk(document, root, 0, entries);
            }
        } catch (RuntimeException e) {
            // A broken outline must not prevent reading the book.
            entries.clear();
        }
        return List.copyOf(entries);
    }

    private static void walk(PDDocument document, PDOutlineNode node, int level, List<OutlineEntry> out) {
        for (PDOutlineItem item : node.children()) {
            if (out.size() > 5000) return; // guard against pathological outlines
            String title = item.getTitle() == null ? "" : item.getTitle().trim();
            int pageIndex = -1;
            try {
                PDPage page = item.findDestinationPage(document);
                if (page != null) pageIndex = document.getPages().indexOf(page);
            } catch (IOException | RuntimeException ignored) {
                // Entries without a resolvable destination are skipped.
            }
            if (pageIndex >= 0 && !title.isEmpty()) {
                out.add(new OutlineEntry(title, pageIndex, level));
            }
            walk(document, item, level + 1, out);
        }
    }
}
