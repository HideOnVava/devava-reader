package com.devavaxp.reader.epub;

import com.devavaxp.reader.epub.EpubBook.TocEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpubParserTest {

    @TempDir
    Path root;

    private void write(String relative, String content) throws IOException {
        Path p = root.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    private static String xhtml(String title, String body) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">"
                + "<head><title>" + title + "</title></head><body>" + body + "</body></html>";
    }

    private void createEpub3() throws IOException {
        write("META-INF/container.xml", "<?xml version=\"1.0\"?>"
                + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                + "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles>"
                + "</container>");
        write("OEBPS/content.opf", "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<package version=\"3.0\" xmlns=\"http://www.idpf.org/2007/opf\" unique-identifier=\"id\">"
                + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>My Novel</dc:title></metadata>"
                + "<manifest>"
                + "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
                + "<item id=\"cover\" href=\"Text/cover.xhtml\" media-type=\"application/xhtml+xml\"/>"
                + "<item id=\"c1\" href=\"Text/Chap%201+a.xhtml\" media-type=\"application/xhtml+xml\"/>"
                + "<item id=\"c2\" href=\"Text/Chap02.xhtml\" media-type=\"application/xhtml+xml\"/>"
                + "<item id=\"notes\" href=\"Text/notes.xhtml\" media-type=\"application/xhtml+xml\"/>"
                + "<item id=\"missing\" href=\"Text/missing.xhtml\" media-type=\"application/xhtml+xml\"/>"
                + "<item id=\"img\" href=\"Images/a.jpg\" media-type=\"image/jpeg\"/>"
                + "</manifest>"
                + "<spine>"
                + "<itemref idref=\"cover\"/><itemref idref=\"c1\"/><itemref idref=\"missing\"/>"
                + "<itemref idref=\"c2\"/><itemref idref=\"c2\"/><itemref idref=\"img\"/>"
                + "<itemref idref=\"notes\" linear=\"no\"/>"
                + "</spine></package>");
        write("OEBPS/nav.xhtml", xhtml("Contents", "<nav epub:type=\"toc\"><ol>"
                + "<li><a href=\"Text/cover.xhtml\">Front&nbsp;cover</a></li>"
                + "<li><a href=\"Text/Chap%201+a.xhtml\">Chapter&nbsp;1</a>"
                + "<ol><li><a href=\"Text/Chap%201+a.xhtml#part2\">Part 2</a></li></ol></li>"
                + "<li><a href=\"Text/Chap02.xhtml\">Chapter&nbsp;2</a></li>"
                + "<li><a href=\"Text/missing.xhtml\">Ghost</a></li>"
                + "</ol></nav>"));
        write("OEBPS/Text/cover.xhtml", xhtml("Cover", "<img src=\"../Images/a.jpg\"/>"));
        write("OEBPS/Text/Chap 1+a.xhtml", xhtml("Chap 1", "<p>" + "text ".repeat(500) + "</p><p id=\"part2\">end</p>"));
        write("OEBPS/Text/Chap02.xhtml", xhtml("Chap 2", "<p>" + "text ".repeat(100) + "</p>"));
        write("OEBPS/Text/notes.xhtml", xhtml("Notes", "<p id=\"n1\">note</p>"));
        write("OEBPS/Images/a.jpg", "not-an-image");
    }

    @Test
    void keepsSpineOrderAndDropsMissingFiles() throws IOException {
        createEpub3();
        EpubBook book = EpubParser.parse(root);

        assertEquals("My Novel", book.getTitle());
        List<String> names = book.getSpine().stream().map(e -> e.file().getFileName().toString()).toList();
        assertEquals(List.of("cover.xhtml", "Chap 1+a.xhtml", "Chap02.xhtml", "notes.xhtml"), names);
        assertTrue(book.getSpine().get(0).linear());
        assertFalse(book.getSpine().get(3).linear());
        assertTrue(book.getSpine().get(1).weight() > book.getSpine().get(2).weight());
    }

    @Test
    void buildsTocFromNavWithLevelsAndFragments() throws IOException {
        createEpub3();
        EpubBook book = EpubParser.parse(root);
        List<TocEntry> toc = book.getToc();

        assertEquals(4, toc.size(), "the entry pointing to a missing file is ignored");
        assertEquals("Front cover", toc.get(0).title());
        assertEquals(0, toc.get(0).spineIndex());
        assertEquals("Chapter 1", toc.get(1).title());
        assertEquals(1, toc.get(1).spineIndex());
        assertEquals(0, toc.get(1).level());
        assertEquals("Part 2", toc.get(2).title());
        assertEquals("part2", toc.get(2).fragment());
        assertEquals(1, toc.get(2).level());
        assertEquals(2, toc.get(3).spineIndex());
        assertNull(toc.get(3).fragment());
    }

    @Test
    void chapterTitleUsesTheNearestPrecedingEntry() throws IOException {
        createEpub3();
        EpubBook book = EpubParser.parse(root);

        assertEquals("Chapter 2", book.chapterTitle(2, "x"));
        assertEquals("Chapter 2", book.chapterTitle(3, "x"), "the notes have no entry: inherit the previous one");
        assertEquals(3, book.tocEntryForChapter(2));
        assertEquals(0, book.indexOfFile(root.resolve("OEBPS/Text/cover.xhtml")));
        assertEquals(-1, book.indexOfFile(root.resolve("OEBPS/nav.xhtml")));
    }

    @Test
    void displayTitlePrefersExactEntryThenDocumentTitle() throws IOException {
        createEpub3();
        EpubBook book = EpubParser.parse(root);

        assertEquals("Chapter 2", book.chapterTitle(2, "Chap 2", "My Novel"), "exact entry wins");
        assertEquals("Notes", book.chapterTitle(3, "Notes", "My Novel"), "document title when informative");
        assertEquals("Chapter 2", book.chapterTitle(3, "My Novel", "My Novel"), "book title is not informative");
        assertEquals("Chapter 2", book.chapterTitle(3, "Chapter", "My Novel"), "a prefix of the nearest entry is not informative");
        assertEquals("Chapter 2", book.chapterTitle(3, null, "My Novel"), "no document title yet: nearest entry");
    }

    @Test
    void progressIsWeightedByChapterSize() throws IOException {
        createEpub3();
        EpubBook book = EpubParser.parse(root);

        assertEquals(0.0, book.progress(0, 0.0), 1e-9);
        assertEquals(1.0, book.progress(3, 1.0), 1e-9);
        double endOfChapter1 = book.progress(1, 1.0);
        double startOfChapter2 = book.progress(2, 0.0);
        assertEquals(endOfChapter1, startOfChapter2, 1e-9);
        assertTrue(endOfChapter1 > 0.5, "chapter 1 is the longest, so finishing it passes the half");
        assertTrue(book.progress(1, 0.5) < endOfChapter1);
    }

    @Test
    void usesNcxWhenThereIsNoNav() throws IOException {
        write("META-INF/container.xml", "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                + "<rootfiles><rootfile full-path=\"content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
        write("content.opf", "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"2.0\">"
                + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>Old</dc:title></metadata>"
                + "<manifest>"
                + "<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>"
                + "<item id=\"a\" href=\"a.html\" media-type=\"text/html\"/>"
                + "<item id=\"b\" href=\"b.html\" media-type=\"text/html\"/>"
                + "</manifest><spine toc=\"ncx\"><itemref idref=\"a\"/><itemref idref=\"b\"/></spine></package>");
        write("toc.ncx", "<?xml version=\"1.0\"?><!DOCTYPE ncx PUBLIC \"-//NISO//DTD ncx 2005-1//EN\" \"http://www.daisy.org/z3986/2005/ncx-2005-1.dtd\">"
                + "<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\"><navMap>"
                + "<navPoint id=\"p1\"><navLabel><text>One</text></navLabel><content src=\"a.html\"/>"
                + "<navPoint id=\"p1b\"><navLabel><text>One bis</text></navLabel><content src=\"a.html#x\"/></navPoint></navPoint>"
                + "<navPoint id=\"p2\"><navLabel><text>Two</text></navLabel><content src=\"b.html\"/></navPoint>"
                + "</navMap></ncx>");
        write("a.html", "<html><body><p id=\"x\">a</p></body></html>");
        write("b.html", "<html><body><p>b</p></body></html>");

        EpubBook book = EpubParser.parse(root);
        assertEquals(2, book.chapterCount());
        assertEquals(3, book.getToc().size());
        assertEquals("One bis", book.getToc().get(1).title());
        assertEquals(1, book.getToc().get(1).level());
        assertEquals("x", book.getToc().get(1).fragment());
        assertEquals(1, book.getToc().get(2).spineIndex());
    }

    @Test
    void withoutContainerFindsOpfAndWithoutOpfSortsByName() throws IOException {
        write("x/package.opf", "<package xmlns=\"http://www.idpf.org/2007/opf\"><manifest>"
                + "<item id=\"b\" href=\"b.xhtml\" media-type=\"application/xhtml+xml\"/>"
                + "<item id=\"a\" href=\"a.xhtml\" media-type=\"application/xhtml+xml\"/>"
                + "</manifest><spine><itemref idref=\"b\"/><itemref idref=\"a\"/></spine></package>");
        write("x/a.xhtml", xhtml("a", "<p>a</p>"));
        write("x/b.xhtml", xhtml("b", "<p>b</p>"));
        EpubBook withOpf = EpubParser.parse(root);
        assertEquals("b.xhtml", withOpf.getSpine().get(0).file().getFileName().toString());

        Files.delete(root.resolve("x/package.opf"));
        EpubBook withoutOpf = EpubParser.parse(root);
        assertEquals(2, withoutOpf.chapterCount());
        assertEquals("a.xhtml", withoutOpf.getSpine().get(0).file().getFileName().toString());
        assertTrue(withoutOpf.getToc().isEmpty());
    }

    @Test
    void decodesPercentEscapesWithoutTouchingPlus() {
        assertEquals("Chap 1+a.xhtml", EpubParser.decode("Chap%201+a.xhtml"));
        assertEquals("año.xhtml", EpubParser.decode("a%C3%B1o.xhtml"));
        assertEquals("100%", EpubParser.decode("100%"));
        assertEquals("%zz", EpubParser.decode("%zz"));
    }

    @Test
    void sanitizesDoctypeAndHtmlEntities() {
        String xml = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"x.dtd\"><p>a&nbsp;b &amp; &eacute; &unknown;</p>";
        String sanitized = EpubParser.sanitize(xml);
        assertEquals("<p>a b &amp; é </p>", sanitized);
    }

    @Test
    void navUnreadableAsXmlIsReadAsText() throws IOException {
        write("META-INF/container.xml", "<container xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
                + "<rootfiles><rootfile full-path=\"content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
        write("content.opf", "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\"><manifest>"
                + "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
                + "<item id=\"a\" href=\"a.xhtml\" media-type=\"application/xhtml+xml\"/>"
                + "</manifest><spine><itemref idref=\"a\"/></spine></package>");
        // The epub prefix is not declared: invalid namespaced XML, but the text is still usable.
        write("nav.xhtml", "<html><body><nav epub:type=\"toc\"><ol><li><a href=\"a.xhtml\">Chap <b>one</b> &amp; two</a></li></ol></nav></body></html>");
        write("a.xhtml", xhtml("a", "<p>a</p>"));

        EpubBook book = EpubParser.parse(root);
        assertEquals(1, book.getToc().size());
        assertEquals("Chap one & two", book.getToc().get(0).title());
    }
}
