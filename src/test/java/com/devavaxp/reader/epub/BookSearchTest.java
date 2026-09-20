package com.devavaxp.reader.epub;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookSearchTest {

    @Test
    void chapterTextDropsMarkupButKeepsWordsApart() {
        String html = """
                <?xml version="1.0"?><!DOCTYPE html>
                <html><head><title>Ignored</title><style>p{color:red}</style></head>
                <body><h1>Chapter&nbsp;1</h1><p>The <em>fer</em>ryman only <span class="dropcap">s</span>hrugged&hellip;</p>
                <script>var x = "<b>no</b>";</script><p>Second&#160;line &amp; more<br/>after the break</p>
                <!-- a comment --><div>1 &lt; 2</div></body></html>
                """;
        assertEquals("Chapter 1 The ferryman only shrugged… Second line & more after the break 1 < 2",
                ChapterText.fromHtml(html));
        assertEquals("", ChapterText.fromHtml(null));
    }

    @Test
    void matchesIgnoringCaseAccentsAndTypographicQuotes() {
        List<BookSearch.Chapter> chapters = List.of(
                new BookSearch.Chapter("La canción del ferryman. “It’s for a traveller,” she said."),
                new BookSearch.Chapter("Nothing here."));
        List<BookSearch.Hit> hits = BookSearch.search(chapters, "CANCION", 10, 40);
        assertEquals(1, hits.size());
        BookSearch.Hit hit = hits.get(0);
        assertEquals(0, hit.chapter());
        assertEquals("canción", hit.match(), "the match keeps the original spelling");
        assertEquals("La ", hit.before());
        assertEquals(" del ferryman. “It’s for a traveller,”", hit.after(), "40 characters, cut at a word end");

        hits = BookSearch.search(chapters, "it's for", 10, 40);
        assertEquals(1, hits.size(), "a plain apostrophe finds the curly one");
        assertEquals("It’s for", hits.get(0).match());
        assertEquals(hits.get(0).start() + hits.get(0).match().length(), hits.get(0).end());
    }

    @Test
    void contextsStopOnWordBoundariesAndHitsAreLimited() {
        String text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho";
        List<BookSearch.Chapter> chapters = List.of(new BookSearch.Chapter(text), new BookSearch.Chapter(text));
        List<BookSearch.Hit> hits = BookSearch.search(chapters, "theta", 10, 12);
        assertEquals(2, hits.size());
        assertEquals("zeta eta ", hits.get(0).before(), "cut at a word start, not mid-word");
        assertEquals(" iota kappa", hits.get(0).after(), "cut at a word end");
        assertEquals(1, hits.get(1).chapter());

        assertTrue(BookSearch.search(chapters, "a", 3, 12).isEmpty(), "single characters are not searched");
        assertEquals(3, BookSearch.search(chapters, "eta", 3, 12).size(), "the limit caps the hits");
        assertTrue(BookSearch.search(chapters, "   ", 3, 12).isEmpty());
        assertEquals("theta", BookSearch.normalizeQuery("  ThEtA "));
        assertEquals("", BookSearch.normalizeQuery("x"));
    }
}
