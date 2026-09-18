package com.devavaxp.reader.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Groups the pages of a fixed-page book into "spreads": what is shown at once.
 * <p>
 * In single-page layout every spread is one page. In double-page layout the cover is shown
 * alone and the rest are paired (1-2, 3-4, ...), which is how printed comics and manga are
 * bound; the last page may end up alone. A wide (landscape) page is a scanned two-page
 * spread already, so it is shown alone too and the pairing carries on after it. Page indices
 * inside a spread are in reading order (lower index first); the reading direction only
 * changes on which side each one is drawn.
 */
public final class PageSpreads {

    private PageSpreads() {
    }

    /** Spreads for {@code pageCount} pages of the same shape. Each spread holds one or two page indices. */
    public static List<int[]> compute(int pageCount, boolean doublePage) {
        return compute(pageCount, doublePage, p -> false);
    }

    /**
     * Spreads for {@code pageCount} pages; {@code wide} tells which pages are landscape and
     * must therefore stand alone in double-page layout.
     */
    public static List<int[]> compute(int pageCount, boolean doublePage, IntPredicate wide) {
        List<int[]> spreads = new ArrayList<>();
        if (pageCount <= 0) return spreads;
        if (!doublePage) {
            for (int p = 0; p < pageCount; p++) spreads.add(new int[]{p});
            return spreads;
        }
        spreads.add(new int[]{0}); // the cover stands alone
        int p = 1;
        while (p < pageCount) {
            boolean pair = p + 1 < pageCount && !wide.test(p) && !wide.test(p + 1);
            spreads.add(pair ? new int[]{p, p + 1} : new int[]{p});
            p += pair ? 2 : 1;
        }
        return spreads;
    }

    /** Index of the spread that contains the page, or 0 if the page is out of range. */
    public static int spreadOf(List<int[]> spreads, int page) {
        for (int i = 0; i < spreads.size(); i++) {
            for (int p : spreads.get(i)) {
                if (p == page) return i;
            }
        }
        return 0;
    }

    /** First page index of a spread. */
    public static int firstPage(List<int[]> spreads, int spread) {
        return spreads.isEmpty() ? 0 : spreads.get(Math.max(0, Math.min(spreads.size() - 1, spread)))[0];
    }

    /** Last page index of a spread. */
    public static int lastPage(List<int[]> spreads, int spread) {
        if (spreads.isEmpty()) return 0;
        int[] s = spreads.get(Math.max(0, Math.min(spreads.size() - 1, spread)));
        return s[s.length - 1];
    }
}
