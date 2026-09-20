package com.devavaxp.reader.epub;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-text search over the chapters of a book.
 * <p>
 * Matching ignores case and diacritics ("cancion" finds "canción") and treats typographic
 * quotes and apostrophes like plain ones. Every hit carries a short context on each side,
 * cut on word boundaries, so it can be shown in a list and — together with the matched text —
 * used by the reader engine to find the same occurrence again inside the rendered chapter.
 */
public final class BookSearch {

    /** Shortest query that is searched; shorter ones would match almost everywhere. */
    public static final int MIN_QUERY_LENGTH = 2;

    /** One occurrence: where it is and how it reads, with {@code start}/{@code end} in the chapter text. */
    public record Hit(int chapter, int start, int end, String before, String match, String after) {
    }

    /** Text prepared for matching: normalized characters and, for each, its index in the source. */
    static final class Normalized {
        final String text;
        final int[] sourceIndex;

        Normalized(String text, int[] sourceIndex) {
            this.text = text;
            this.sourceIndex = sourceIndex;
        }
    }

    /** A chapter's text with its normalized form, computed once and reused by every search. */
    public static final class Chapter {
        private final String text;
        private final Normalized normalized;

        public Chapter(String text) {
            this.text = text == null ? "" : text;
            this.normalized = normalize(this.text);
        }

        public String text() {
            return text;
        }
    }

    private BookSearch() {
    }

    /**
     * Normalizes for matching: lower case, diacritics removed, typographic quotes unified.
     * Each output character comes from exactly one input character (combining marks are
     * dropped), so positions can be mapped back to the source.
     */
    static Normalized normalize(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int[] map = new int[s.length()];
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            char base = c;
            if (c > 127) {
                String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
                if (!decomposed.isEmpty()) base = decomposed.charAt(0);
            }
            base = switch (base) {
                case '‘', '’', '‚', '′', '´', '`' -> '\'';
                case '“', '”', '„', '″', '«', '»' -> '"';
                case '‐', '‑', '‒', '–', '—' -> '-';
                case ' ' -> ' ';
                default -> Character.toLowerCase(base);
            };
            out.append(base);
            map[n++] = i;
        }
        int[] trimmed = new int[n];
        System.arraycopy(map, 0, trimmed, 0, n);
        return new Normalized(out.toString(), trimmed);
    }

    /** The query as it is matched: trimmed, single spaces, normalized. Empty when too short. */
    public static String normalizeQuery(String query) {
        String q = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        String normalized = normalize(q).text;
        return normalized.length() < MIN_QUERY_LENGTH ? "" : normalized;
    }

    /**
     * Finds the query in the chapters, in reading order, up to {@code limit} hits.
     * {@code contextChars} is the approximate length of the text kept on each side of a hit.
     */
    public static List<Hit> search(List<Chapter> chapters, String query, int limit, int contextChars) {
        List<Hit> hits = new ArrayList<>();
        String needle = normalizeQuery(query);
        if (needle.isEmpty()) return hits;
        for (int c = 0; c < chapters.size() && hits.size() < limit; c++) {
            Chapter chapter = chapters.get(c);
            String hay = chapter.normalized.text;
            int[] map = chapter.normalized.sourceIndex;
            int from = 0;
            while (hits.size() < limit) {
                int at = hay.indexOf(needle, from);
                if (at < 0) break;
                int start = map[at];
                int end = map[at + needle.length() - 1] + 1;
                hits.add(new Hit(c, start, end, contextBefore(chapter.text, start, contextChars),
                        chapter.text.substring(start, end), contextAfter(chapter.text, end, contextChars)));
                from = at + needle.length();
            }
        }
        return hits;
    }

    /** Up to {@code chars} characters before {@code start}, starting on a word boundary. */
    static String contextBefore(String text, int start, int chars) {
        int from = Math.max(0, start - chars);
        if (from > 0) {
            int space = text.indexOf(' ', from);
            if (space >= 0 && space < start) from = space + 1; else from = start;
        }
        return text.substring(from, start);
    }

    /** Up to {@code chars} characters after {@code end}, ending on a word boundary. */
    static String contextAfter(String text, int end, int chars) {
        int to = Math.min(text.length(), end + chars);
        if (to < text.length()) {
            int space = text.lastIndexOf(' ', to);
            if (space > end) to = space;
        }
        return text.substring(end, to);
    }
}
