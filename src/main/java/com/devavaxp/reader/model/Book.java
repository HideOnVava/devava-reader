package com.devavaxp.reader.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A single volume (one .epub or .pdf file) that belongs to a {@link BookCollection}.
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
    /** Fixed-page document (comics/manga) read with the page-image reader. */
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

    /** Bookmarks in reading order; missing in files written before bookmarks existed. */
    @SerializedName("bookmarks")
    private List<Bookmark> bookmarks;

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
        this.bookmarks = new ArrayList<>();
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
    // Bookmarks
    // ------------------------------------------------------------------

    /** Bookmarks sorted by position (read-only view). */
    public List<Bookmark> getBookmarks() {
        return Collections.unmodifiableList(bookmarkList());
    }

    /** Adds a bookmark and keeps the list in reading order. */
    public void addBookmark(Bookmark bookmark) {
        List<Bookmark> list = bookmarkList();
        list.add(bookmark);
        list.sort(Bookmark.BY_POSITION);
    }

    public boolean removeBookmark(Bookmark bookmark) {
        return bookmarkList().remove(bookmark);
    }

    /**
     * The bookmark that points at a given place, if any. Two positions are the same place
     * when they are in the same chapter (page, for a PDF) and closer than {@code tolerance}
     * in fraction — callers pass half the width of a page so that a bookmark set on a page
     * is found again from anywhere on that page.
     */
    public Bookmark findBookmark(int chapter, double fraction, double tolerance) {
        for (Bookmark b : bookmarkList()) {
            if (b.getChapter() == chapter && Math.abs(b.getFraction() - fraction) <= tolerance) return b;
        }
        return null;
    }

    private List<Bookmark> bookmarkList() {
        if (bookmarks == null) bookmarks = new ArrayList<>(); // file written before bookmarks existed
        return bookmarks;
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
