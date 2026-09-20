package com.devavaxp.reader.epub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Plain text of an XHTML chapter, for searching: no markup, scripts or styles, entities
 * decoded, whitespace collapsed. Block-level elements become a space so that words of
 * neighbouring paragraphs do not run together, while inline elements (a span inside a
 * word, an emphasised syllable) vanish without a trace — the same text the browser's DOM
 * text nodes contain once joined, which is what the reader engine searches when jumping to
 * a match.
 */
public final class ChapterText {

    private static final Pattern DROPPED = Pattern.compile(
            "(?is)<!--.*?-->|<script\\b[^>]*>.*?</script>|<style\\b[^>]*>.*?</style>|<head\\b[^>]*>.*?</head>");
    private static final Pattern BLOCK_TAG = Pattern.compile(
            "(?is)</?(?:p|div|br|hr|h[1-6]|li|ul|ol|dl|dt|dd|tr|td|th|table|thead|tbody|tfoot|caption|section|article|"
                    + "header|footer|aside|nav|main|body|html|blockquote|pre|figure|figcaption|address|title|"
                    + "summary|details|form|fieldset|legend|option|select|textarea)\\b[^>]*>");
    private static final Pattern ANY_TAG = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0\\u2007\\u202F]+");

    private ChapterText() {
    }

    /** Reads a chapter file and returns its text (see the class description). */
    public static String extract(Path file) throws IOException {
        String html = Files.readString(file, StandardCharsets.UTF_8);
        if (!html.isEmpty() && html.charAt(0) == '﻿') html = html.substring(1);
        return fromHtml(html);
    }

    /** Text of an XHTML/HTML fragment (see the class description). */
    public static String fromHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        String s = DROPPED.matcher(html).replaceAll(" ");
        s = BLOCK_TAG.matcher(s).replaceAll(" ");
        s = ANY_TAG.matcher(s).replaceAll("");
        s = EpubParser.unescapeHtml(s);
        return SPACES.matcher(s).replaceAll(" ").trim();
    }
}
