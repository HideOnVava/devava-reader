package com.devavaxp.reader.model;

import com.google.gson.annotations.SerializedName;

import java.util.Comparator;
import java.util.UUID;

/**
 * A place the reader wants to come back to, with an optional one-line note.
 * <p>
 * The position uses the same coordinates as the saved reading position: for an EPUB,
 * {@code chapter} is the spine index and {@code fraction} (0..1) the position inside that
 * chapter; for a PDF, {@code chapter} is the page index and {@code fraction} is 0. The
 * {@code excerpt} holds the first words of the passage (EPUB) so the bookmark can be
 * recognised in a list without opening the book.
 */
public class Bookmark {

    /** Sorts by position in the book. */
    public static final Comparator<Bookmark> BY_POSITION =
            Comparator.comparingInt(Bookmark::getChapter).thenComparingDouble(Bookmark::getFraction);

    @SerializedName("id")
    private String id;

    @SerializedName("chapter")
    private int chapter;

    @SerializedName("fraction")
    private double fraction;

    @SerializedName("excerpt")
    private String excerpt;

    @SerializedName("note")
    private String note;

    /** Epoch milliseconds of creation. */
    @SerializedName("createdAt")
    private long createdAt;

    public Bookmark(int chapter, double fraction, String excerpt) {
        this.id = UUID.randomUUID().toString();
        this.chapter = Math.max(0, chapter);
        this.fraction = Math.max(0.0, Math.min(1.0, fraction));
        this.excerpt = excerpt == null ? "" : excerpt.trim();
        this.note = "";
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }

    public int getChapter() { return chapter; }

    public double getFraction() { return fraction; }

    public String getExcerpt() { return excerpt == null ? "" : excerpt; }

    public String getNote() { return note == null ? "" : note; }

    public void setNote(String note) { this.note = note == null ? "" : note.trim(); }

    public boolean hasNote() { return !getNote().isEmpty(); }

    public long getCreatedAt() { return createdAt; }
}
