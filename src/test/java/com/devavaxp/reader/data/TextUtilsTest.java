package com.devavaxp.reader.data;

import com.devavaxp.reader.model.Book;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextUtilsTest {

    @Test
    void naturalOrderOfVolumes() {
        List<String> names = new ArrayList<>(List.of(
                "Volume 10.epub", "Volume 2.epub", "Volume 1.epub", "volume 02b.epub", "Extra.epub"));
        names.sort(TextUtils::compareNatural);
        assertEquals(List.of("Extra.epub", "Volume 1.epub", "Volume 2.epub", "volume 02b.epub", "Volume 10.epub"), names);
    }

    @Test
    void titleFromFileName() {
        assertEquals("[RVN] Mushoku Tensei - Volume 02 [SC]", TextUtils.titleFromFile(new File("C:/x/[RVN] Mushoku Tensei - Volume 02 [SC].EPUB")));
        assertEquals("no-extension", TextUtils.titleFromFile(new File("no-extension")));
        assertEquals("One Piece v01", TextUtils.titleFromFile(new File("D:/manga/One Piece v01.pdf")));
        assertEquals("Chapter 1.part", TextUtils.titleFromFile(new File("Chapter 1.part.PDF")));
    }

    @Test
    void savedPositionOfABook() {
        Book b = new Book("c", "t", "r", 1);
        assertEquals(0, b.getSavedChapter());
        assertEquals(0.0, b.getSavedFraction(), 1e-9);

        b.setPosition(12, 0.375);
        assertEquals("12:0.37500", b.getSavedPosition());
        assertEquals(12, b.getSavedChapter());
        assertEquals(0.375, b.getSavedFraction(), 1e-9);

        b.setSavedPosition("abc");
        assertEquals(0, b.getSavedChapter());
        b.setSavedPosition("7:x");
        assertEquals(7, b.getSavedChapter());
        assertEquals(0.0, b.getSavedFraction(), 1e-9);

        b.setReadingPercentage(150);
        assertEquals(100.0, b.getReadingPercentage(), 1e-9);
        b.resetProgress();
        assertEquals("", b.getSavedPosition());
        assertEquals(0.0, b.getReadingPercentage(), 1e-9);
    }

    @Test
    void bookFormatComesFromTheFileExtension() {
        assertEquals(Book.FORMAT_EPUB, Book.formatOf("C:/x/Volume 1.epub"));
        assertEquals(Book.FORMAT_PDF, Book.formatOf("C:/x/Chapter 1.PDF"));
        assertEquals(Book.FORMAT_EPUB, Book.formatOf(null));
        assertEquals(Book.FORMAT_PDF, new Book("c", "t", "manga.pdf", 1).getFormat());
        Book b = new Book("c", "t", "novel.epub", 1);
        b.setFormat("weird");
        assertEquals(Book.FORMAT_EPUB, b.getFormat(), "unknown values fall back to EPUB");
    }

    @Test
    void plurals() {
        assertEquals("1 volume", TextUtils.plural(1, "volume", "volumes"));
        assertEquals("3 volumes", TextUtils.plural(3, "volume", "volumes"));
    }
}
