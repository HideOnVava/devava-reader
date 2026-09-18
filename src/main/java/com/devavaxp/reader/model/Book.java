package com.devavaxp.reader.model;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;
import java.util.UUID;

/**
 * A single volume (one .epub file) that belongs to a {@link BookCollection}.
 * <p>
 * The reading position is stored as {@code "chapter:fraction"}, where {@code chapter} is
 * the index inside the EPUB spine and {@code fraction} (0..1) is the relative position
 * within that chapter. Older files that only stored the chapter number remain valid.
 * <p>
 * JSON field names carry {@code alternate} values so that library files written by
 * earlier versions of the application (Spanish field names) can still be loaded.
 */
public class Book {

    /** Reflowable e-book read with the pagination engine. */
    public static final String FORMAT_EPUB = "epub";
    /** Fixed-page document (comics/manga). Only recorded for now; a PDF reader is not implemented yet. */
    public static final String FORMAT_PDF = "pdf";

    @SerializedName("id")
    private String id;

    @SerializedName(value = "collectionId", alternate = {"idColeccion"})
    private String collectionId;

    @SerializedName(value = "title", alternate = {"titulo"})
    private String title;

    @SerializedName(value = "filePath", alternate = {"rutaArchivo"})
    private String filePath;

    @SerializedName(value = "order", alternate = {"orden"})
    private int order;

    @SerializedName(value = "readingPercentage", alternate = {"porcentajeLectura"})
    private double readingPercentage;

    @SerializedName(value = "read", alternate = {"leido"})
    private boolean read;

    @SerializedName(value = "savedPosition", alternate = {"posicionGuardada"})
    private String savedPosition;

    /** Epoch milliseconds of the last time the book was opened; 0 if never. */
    @SerializedName(value = "lastReadAt", alternate = {"ultimaLectura"})
    private long lastReadAt;

    /** File format ({@link #FORMAT_EPUB} or {@link #FORMAT_PDF}); missing in older files means EPUB. */
    @SerializedName("format")
    private String format;

    public Book(String collectionId, String title, String filePath, int order) {
        this.id = UUID.randomUUID().toString();
        this.collectionId = collectionId;
        this.title = title;
        this.filePath = filePath;
        this.order = order;
        this.readingPercentage = 0.0;
        this.read = false;
        this.savedPosition = "";
        this.lastReadAt = 0L;
        this.format = formatOf(filePath);
    }

    /** Format implied by a file name: {@code .pdf} is PDF, anything else is treated as EPUB. */
    public static String formatOf(String filePath) {
        String name = filePath == null ? "" : filePath.trim().toLowerCase(Locale.ROOT);
        return name.endsWith(".pdf") ? FORMAT_PDF : FORMAT_EPUB;
    }

    // ------------------------------------------------------------------
    // Basic accessors
    // ------------------------------------------------------------------

    public String getId() { return id; }

    public String getCollectionId() { return collectionId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }

    public double getReadingPercentage() { return readingPercentage; }
    public void setReadingPercentage(double percentage) {
        this.readingPercentage = Math.max(0.0, Math.min(100.0, percentage));
    }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public String getSavedPosition() { return savedPosition == null ? "" : savedPosition; }
    public void setSavedPosition(String position) { this.savedPosition = position; }

    public long getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(long lastReadAt) { this.lastReadAt = lastReadAt; }

    /** Normalized format: PDF when recorded as such, EPUB otherwise (including older files). */
    public String getFormat() {
        return FORMAT_PDF.equalsIgnoreCase(format) ? FORMAT_PDF : FORMAT_EPUB;
    }

    /** The format exactly as stored; null when the file was written before formats existed. */
    public String getRawFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    // ------------------------------------------------------------------
    // Reading position helpers
    // ------------------------------------------------------------------

    /** Stores the reading position in its structured form. */
    public void setPosition(int chapter, double fraction) {
        this.savedPosition = chapter + ":" + String.format(Locale.ROOT, "%.5f", fraction);
    }

    /** Saved chapter index (0 when there is no position). */
    public int getSavedChapter() {
        String p = getSavedPosition();
        if (p.isEmpty()) return 0;
        int sep = p.indexOf(':');
        try {
            return Math.max(0, Integer.parseInt(sep >= 0 ? p.substring(0, sep) : p));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Saved fraction (0..1) inside the saved chapter. */
    public double getSavedFraction() {
        String p = getSavedPosition();
        int sep = p.indexOf(':');
        if (sep < 0) return 0.0;
        try {
            double f = Double.parseDouble(p.substring(sep + 1));
            return Math.max(0.0, Math.min(1.0, f));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Returns the book to its initial state: no progress and no position. */
    public void resetProgress() {
        this.readingPercentage = 0.0;
        this.read = false;
        this.savedPosition = "";
    }

    /** Whether reading has started at all. */
    public boolean isStarted() {
        return !getSavedPosition().isEmpty() || readingPercentage > 0 || read;
    }

    @Override
    public String toString() {
        return title;
    }
}
