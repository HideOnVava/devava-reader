package com.devavaxp.reader.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * A book made of fixed pages that can be rendered as images (comics, manga, scans).
 * <p>
 * This is the seam between the fixed-page reader and the file formats: the reader only
 * needs a page count, the proportions of each page and a way to render a page at a given
 * scale. {@link PdfPageSource} implements it with PDFBox; an archive of images (CBZ) could
 * implement it later without touching the reader.
 */
public interface PageSource extends AutoCloseable {

    /** Proportions of a page in abstract units (points for PDF); only the ratio matters. */
    record PageSize(double width, double height) {
        public double aspect() {
            return height <= 0 ? 1.0 : width / height;
        }
    }

    /** An entry of the document outline (table of contents). */
    record OutlineEntry(String title, int pageIndex, int level) {
    }

    /** Document title, or an empty string. */
    String title();

    int pageCount();

    /** Size of the page as it is displayed (rotation already applied). */
    PageSize pageSize(int index);

    /**
     * Renders a page as an RGB image; {@code pixelsPerUnit} maps the page units of
     * {@link #pageSize} to pixels. Implementations may be called from a background thread,
     * one render at a time.
     */
    BufferedImage render(int index, double pixelsPerUnit) throws IOException;

    /** Outline entries in document order (empty if the document has none). */
    List<OutlineEntry> outline();

    @Override
    void close();
}
