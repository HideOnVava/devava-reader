package com.devavaxp.reader.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkTest {

    @Test
    void bookmarksAreKeptInReadingOrder() {
        Book book = new Book("c", "Vol 1", "C:/x/1.epub", 1);
        Bookmark late = new Bookmark(4, 0.5, "late");
        Bookmark early = new Bookmark(1, 0.9, "early");
        Bookmark middle = new Bookmark(4, 0.1, "middle");
        book.addBookmark(late);
        book.addBookmark(early);
        book.addBookmark(middle);
        assertEquals(List.of(early, middle, late), book.getBookmarks());
        assertThrows(UnsupportedOperationException.class, () -> book.getBookmarks().add(early),
                "the list handed out is read-only");
    }

    @Test
    void findsTheBookmarkOfAPageWithinTheTolerance() {
        Book book = new Book("c", "Vol 1", "C:/x/1.epub", 1);
        Bookmark mark = new Bookmark(2, 0.50, "");
        book.addBookmark(mark);
        assertSame(mark, book.findBookmark(2, 0.50, 0.0), "exact match");
        assertSame(mark, book.findBookmark(2, 0.53, 0.05), "same page: within half a page");
        assertNull(book.findBookmark(2, 0.60, 0.05), "another page");
        assertNull(book.findBookmark(3, 0.50, 0.05), "another chapter");
        assertTrue(book.removeBookmark(mark));
        assertFalse(book.removeBookmark(mark), "already removed");
        assertTrue(book.getBookmarks().isEmpty());
    }

    @Test
    void notesAndExcerptsAreTrimmedAndNeverNull() {
        Bookmark b = new Bookmark(-3, 1.7, "  Once upon a time  ");
        assertEquals(0, b.getChapter(), "chapter is clamped");
        assertEquals(1.0, b.getFraction(), 1e-9, "fraction is clamped");
        assertEquals("Once upon a time", b.getExcerpt());
        assertFalse(b.hasNote());
        b.setNote("  remember this  ");
        assertEquals("remember this", b.getNote());
        assertTrue(b.hasNote());
        b.setNote(null);
        assertEquals("", b.getNote());
        assertNotNull(b.getId());
        assertTrue(b.getCreatedAt() > 0);
    }

    @Test
    void resettingProgressKeepsBookmarks() {
        Book book = new Book("c", "Vol 1", "C:/x/1.epub", 1);
        book.addBookmark(new Bookmark(0, 0.0, "start"));
        book.setPosition(3, 0.5);
        book.resetProgress();
        assertEquals(1, book.getBookmarks().size());
    }
}
