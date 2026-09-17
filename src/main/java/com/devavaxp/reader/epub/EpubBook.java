package com.devavaxp.reader.epub;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of parsing an extracted EPUB: the reading order (spine), the table of contents
 * and the "weight" of every chapter, used to estimate real reading progress.
 */
public final class EpubBook {

    /** One XHTML document of the reading order. {@code weight} is its size in bytes (at least 1). */
    public record SpineItem(Path file, long weight, boolean linear) {
        public SpineItem {
            Objects.requireNonNull(file);
            weight = Math.max(1, weight);
        }
    }

    /** One entry of the table of contents: title, chapter (spine index), optional fragment and nesting level. */
    public record TocEntry(String title, int spineIndex, String fragment, int level) {
        public TocEntry {
            title = title == null ? "" : title.trim();
            fragment = fragment == null || fragment.isEmpty() ? null : fragment;
            level = Math.max(0, level);
        }
    }

    private final String title;
    private final List<SpineItem> spine;
    private final List<TocEntry> toc;
    private final long[] cumulativeWeight; // cumulativeWeight[i] = sum of the weights of the chapters before i
    private final long totalWeight;

    public EpubBook(String title, List<SpineItem> spine, List<TocEntry> toc) {
        this.title = title == null ? "" : title.trim();
        this.spine = List.copyOf(spine);
        this.toc = List.copyOf(toc);
        this.cumulativeWeight = new long[this.spine.size()];
        long sum = 0;
        for (int i = 0; i < this.spine.size(); i++) {
            cumulativeWeight[i] = sum;
            sum += this.spine.get(i).weight();
        }
        this.totalWeight = Math.max(1, sum);
    }

    public String getTitle() {
        return title;
    }

    public List<SpineItem> getSpine() {
        return spine;
    }

    public List<TocEntry> getToc() {
        return toc;
    }

    public int chapterCount() {
        return spine.size();
    }

    public boolean isEmpty() {
        return spine.isEmpty();
    }

    // ------------------------------------------------------------------
    // Progress
    // ------------------------------------------------------------------

    /**
     * Overall progress (0..1) when standing in chapter {@code index} with a fraction
     * {@code chapterFraction} (0..1) of that chapter already read. Weighted by chapter
     * size, so an illustration page does not count the same as a long chapter.
     */
    public double progress(int index, double chapterFraction) {
        if (spine.isEmpty()) return 0.0;
        int i = Math.max(0, Math.min(spine.size() - 1, index));
        double f = Math.max(0.0, Math.min(1.0, chapterFraction));
        double readSoFar = cumulativeWeight[i] + f * spine.get(i).weight();
        return Math.max(0.0, Math.min(1.0, readSoFar / totalWeight));
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    /** Spine index of the given file, or -1 if it is not part of the reading order. */
    public int indexOfFile(Path file) {
        if (file == null) return -1;
        Path wanted = file.toAbsolutePath().normalize();
        for (int i = 0; i < spine.size(); i++) {
            if (spine.get(i).file().toAbsolutePath().normalize().equals(wanted)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Chapter title according to the table of contents: the last entry whose chapter is
     * before or equal to the given one. Returns {@code fallback} when there is none.
     */
    public String chapterTitle(int spineIndex, String fallback) {
        TocEntry best = null;
        for (TocEntry e : toc) {
            if (e.spineIndex() <= spineIndex && (best == null || e.spineIndex() >= best.spineIndex())) {
                best = e;
            }
        }
        if (best != null && !best.title().isEmpty()) return best.title();
        return fallback == null ? "" : fallback;
    }

    /**
     * Title to display for a chapter: the exact table-of-contents entry if there is one;
     * otherwise the document's {@code <title>} when it is informative (not just the book
     * title, and not a mere prefix of the nearest preceding entry, e.g. "Chapter 8" when the
     * contents say "Chapter 8: Clumsy"); and finally the nearest preceding entry.
     */
    public String chapterTitle(int spineIndex, String documentTitle, String bookTitle) {
        for (TocEntry e : toc) {
            if (e.spineIndex() == spineIndex && e.fragment() == null && !e.title().isEmpty()) {
                return e.title();
            }
        }
        String doc = documentTitle == null ? "" : documentTitle.trim();
        String nearest = chapterTitle(spineIndex, "");
        boolean informative = !doc.isEmpty()
                && !doc.equalsIgnoreCase(bookTitle == null ? "" : bookTitle.trim())
                && !doc.equalsIgnoreCase(title)
                && !(nearest.length() > doc.length() && nearest.toLowerCase().startsWith(doc.toLowerCase()));
        if (informative) {
            return doc;
        }
        return nearest.isEmpty() ? doc : nearest;
    }

    /** Position in the table of contents of the entry that corresponds to the chapter, or -1. */
    public int tocEntryForChapter(int spineIndex) {
        int best = -1;
        for (int i = 0; i < toc.size(); i++) {
            TocEntry e = toc.get(i);
            if (e.spineIndex() <= spineIndex && (best < 0 || e.spineIndex() >= toc.get(best).spineIndex())) {
                best = i;
            }
        }
        return best;
    }

    /** A book without content, useful as a safe placeholder. */
    public static EpubBook empty() {
        return new EpubBook("", Collections.emptyList(), new ArrayList<>());
    }
}
