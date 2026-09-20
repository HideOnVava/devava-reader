package com.devavaxp.reader.data;

import com.devavaxp.reader.model.Book;
import com.devavaxp.reader.model.BookCollection;
import com.devavaxp.reader.model.Bookmark;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataManagerTest {

    @TempDir
    Path folder;

    private Path file() {
        return folder.resolve("sub").resolve("library.json");
    }

    @Test
    void savesAndReloadsCollectionsBooksAndPreferences() throws IOException {
        DataManager dm = new DataManager(file());
        BookCollection c = new BookCollection("Mushoku");
        dm.addCollection(c);
        dm.addBook(new Book(c.getId(), "Vol 1", "C:/x/1.epub", 5));
        dm.addBook(new Book(c.getId(), "Vol 2", "C:/x/2.epub", 9));
        dm.getPreferences().setFontSize(24);
        dm.getPreferences().setTheme("dark");
        dm.save();

        DataManager dm2 = new DataManager(file());
        assertEquals(1, dm2.getCollections().size());
        List<Book> books = dm2.getBooksOf(c.getId());
        assertEquals(List.of("Vol 1", "Vol 2"), books.stream().map(Book::getTitle).toList());
        assertEquals(List.of(1, 2), books.stream().map(Book::getOrder).toList(), "order is normalized to 1..n");
        assertEquals(24, dm2.getPreferences().getFontSize());
        assertEquals("dark", dm2.getPreferences().getTheme());
        assertFalse(Files.exists(file().resolveSibling("library.json.tmp")), "no temporary files are left behind");

        String json = Files.readString(file(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"collections\"") && json.contains("\"filePath\"") && json.contains("\"fontSize\""),
                "new files use the English field names");
    }

    @Test
    void readsTheLegacyFormatWithSpanishFieldNames() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), """
                {
                  "colecciones": [ { "id": "c1", "titulo": "Old" } ],
                  "libros": [
                    { "id": "l1", "idColeccion": "c1", "titulo": "V1", "rutaArchivo": "C:\\\\a.epub",
                      "orden": 1, "porcentajeLectura": 29.7, "leido": false, "posicionGuardada": "41" }
                  ],
                  "preferencias": { "tamanoFuente": 22, "tema": "oscuro", "tipografia": "sans", "columnas": 1 }
                }
                """, StandardCharsets.UTF_8);
        DataManager dm = new DataManager(file());
        assertEquals("Old", dm.getCollections().get(0).getTitle());
        Book b = dm.getBooksOf("c1").get(0);
        assertEquals("V1", b.getTitle());
        assertEquals("C:\\a.epub", b.getFilePath());
        assertEquals(29.7, b.getReadingPercentage(), 1e-9);
        assertEquals(41, b.getSavedChapter());
        assertEquals(0.0, b.getSavedFraction(), 1e-9);
        assertEquals(0L, b.getLastReadAt());
        assertTrue(b.isStarted());
        assertEquals(Book.FORMAT_EPUB, b.getFormat(), "books without a recorded format are EPUB");
        assertEquals(Book.FORMAT_EPUB, b.getRawFormat(), "the format is filled in on load so it gets persisted");
        assertEquals(22, dm.getPreferences().getFontSize());
        assertEquals("dark", dm.getPreferences().getTheme(), "legacy theme names are mapped");
        assertEquals("sans", dm.getPreferences().getTypeface());
        assertEquals(1, dm.getPreferences().getColumns());
    }

    @Test
    void missingPreferencesFallBackToDefaults() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{ \"collections\": [ { \"id\": \"c1\", \"title\": \"Only\" } ] }", StandardCharsets.UTF_8);
        DataManager dm = new DataManager(file());
        assertEquals(1, dm.getCollections().size());
        assertTrue(dm.getBooks().isEmpty());
        assertEquals(0, dm.countBooks(dm.getCollections().get(0)));
        assertEquals(20, dm.getPreferences().getFontSize());
        assertEquals("light", dm.getPreferences().getTheme());
        assertEquals(2, dm.getPreferences().getColumns());
    }

    @Test
    void corruptFileIsBackedUpAndAFreshLibraryStarts() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "{ this is not json", StandardCharsets.UTF_8);
        DataManager dm = new DataManager(file());
        assertTrue(dm.getCollections().isEmpty());
        try (Stream<Path> s = Files.list(file().getParent())) {
            assertTrue(s.anyMatch(p -> p.getFileName().toString().startsWith("library.corrupt-")));
        }
    }

    @Test
    void saveIfMissingCreatesTheFileAndItsFolder() {
        DataManager dm = new DataManager(file());
        assertFalse(Files.exists(file()));
        dm.saveIfMissing();
        assertTrue(Files.isRegularFile(file()));
    }

    @Test
    void movingAndDeletingKeepConsecutiveOrder() {
        DataManager dm = new DataManager(file());
        BookCollection c = new BookCollection("Series");
        dm.addCollection(c);
        Book a = new Book(c.getId(), "A", "a.epub", 1);
        Book b = new Book(c.getId(), "B", "b.epub", 2);
        Book d = new Book(c.getId(), "D", "d.epub", 3);
        dm.addBooks(List.of(a, b, d));

        assertEquals(1, dm.moveBook(a, 1), "A moves down to index 1");
        assertEquals(List.of("B", "A", "D"), titles(dm, c));
        assertEquals(-1, dm.moveBook(b, -1), "B is already at the top");
        assertEquals(0, dm.moveBook(d, -5), "clamped to the beginning");
        assertEquals(List.of("D", "B", "A"), titles(dm, c));

        dm.deleteBook(b);
        assertEquals(List.of("D", "A"), titles(dm, c));
        assertEquals(List.of(1, 2), dm.getBooksOf(c.getId()).stream().map(Book::getOrder).toList());
        assertEquals(3, dm.nextOrder(c.getId()));

        dm.deleteCollection(c);
        assertTrue(dm.getCollections().isEmpty());
        assertTrue(dm.getBooks().isEmpty());
    }

    @Test
    void detectsDuplicatePathsAndLastReadBook() {
        DataManager dm = new DataManager(file());
        BookCollection c = new BookCollection("Series");
        dm.addCollection(c);
        Book a = new Book(c.getId(), "A", folder.resolve("a.epub").toString(), 1);
        Book b = new Book(c.getId(), "B", folder.resolve("b.epub").toString(), 2);
        dm.addBooks(List.of(a, b));
        assertTrue(dm.hasBookWithPath(c.getId(), folder.resolve("./a.epub").toString()));
        assertFalse(dm.hasBookWithPath(c.getId(), folder.resolve("c.epub").toString()));

        assertTrue(dm.lastReadBook().isEmpty());
        a.setLastReadAt(100);
        b.setLastReadAt(200);
        assertEquals("B", dm.lastReadBook().orElseThrow().getTitle());
        b.setRead(true);
        assertEquals("A", dm.lastReadBook().orElseThrow().getTitle(), "finished books do not count");
    }

    // ------------------------------------------------------------------
    // Collection order, pinning and format filter
    // ------------------------------------------------------------------

    @Test
    void legacyCollectionsKeepFileOrderAndGetPositions() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), """
                { "colecciones": [ { "id": "b", "titulo": "Beta" }, { "id": "a", "titulo": "Alpha" } ], "libros": [] }
                """, StandardCharsets.UTF_8);
        DataManager dm = new DataManager(file());
        assertEquals(List.of("Beta", "Alpha"), collectionTitles(dm), "no positions in the file: file order is kept");
        assertEquals(List.of(1, 2), dm.getCollections().stream().map(BookCollection::getOrder).toList());
        assertFalse(dm.getCollections().get(0).isPinned());
    }

    @Test
    void pinnedCollectionsAreListedFirstKeepingRelativeOrder() {
        DataManager dm = new DataManager(file());
        BookCollection a = new BookCollection("A");
        BookCollection b = new BookCollection("B");
        BookCollection c = new BookCollection("C");
        BookCollection d = new BookCollection("D");
        for (BookCollection x : List.of(a, b, c, d)) dm.addCollection(x);
        assertEquals(List.of("A", "B", "C", "D"), collectionTitles(dm));

        dm.setCollectionPinned(c, true);
        assertEquals(List.of("C", "A", "B", "D"), collectionTitles(dm));
        dm.setCollectionPinned(a, true);
        assertEquals(List.of("C", "A", "B", "D"), collectionTitles(dm), "A joins the pinned group after C");
        dm.setCollectionPinned(c, false);
        assertEquals(List.of("A", "C", "B", "D"), collectionTitles(dm), "C stays where it was: right after the pinned group");
        assertEquals(List.of(1, 2, 3, 4), dm.getCollections().stream().map(BookCollection::getOrder).toList());

        DataManager reloaded = new DataManager(file());
        assertEquals(List.of("A", "C", "B", "D"), collectionTitles(reloaded));
        assertTrue(reloaded.getCollections().get(0).isPinned(), "pinning survives a reload");
        assertFalse(reloaded.getCollections().get(1).isPinned());
    }

    @Test
    void movingCollectionsStaysInsideThePinnedOrRegularGroup() {
        DataManager dm = new DataManager(file());
        BookCollection a = new BookCollection("A");
        BookCollection b = new BookCollection("B");
        BookCollection c = new BookCollection("C");
        BookCollection d = new BookCollection("D");
        for (BookCollection x : List.of(a, b, c, d)) dm.addCollection(x);
        dm.setCollectionPinned(a, true);
        dm.setCollectionPinned(b, true);
        assertEquals(List.of("A", "B", "C", "D"), collectionTitles(dm));

        assertEquals(1, dm.moveCollection(a, 1), "A moves down inside the pinned group");
        assertEquals(List.of("B", "A", "C", "D"), collectionTitles(dm));
        assertFalse(dm.canMoveCollection(a, 1), "A cannot cross into the regular group");
        assertEquals(-1, dm.moveCollection(a, 1));
        assertFalse(dm.canMoveCollection(c, -1), "C cannot cross into the pinned group");
        assertEquals(-1, dm.moveCollection(c, -1));
        assertTrue(dm.canMoveCollection(c, 1));
        assertEquals(3, dm.moveCollection(c, 1));
        assertEquals(List.of("B", "A", "D", "C"), collectionTitles(dm));
        assertEquals(2, dm.moveCollection(c, -5), "clamped to the start of its group");
        assertEquals(List.of("B", "A", "C", "D"), collectionTitles(dm));

        assertEquals(List.of("B", "A", "C", "D"), collectionTitles(new DataManager(file())), "order survives a reload");
    }

    @Test
    void addingAndDeletingCollectionsRenumbersPositions() {
        DataManager dm = new DataManager(file());
        BookCollection a = new BookCollection("A");
        BookCollection b = new BookCollection("B");
        BookCollection c = new BookCollection("C");
        for (BookCollection x : List.of(a, b, c)) dm.addCollection(x);
        dm.setCollectionPinned(c, true);
        BookCollection d = new BookCollection("D");
        dm.addCollection(d);
        assertEquals(List.of("C", "A", "B", "D"), collectionTitles(dm), "new collections go last, after pinned ones");

        dm.deleteCollection(a);
        assertEquals(List.of("C", "B", "D"), collectionTitles(dm));
        assertEquals(List.of(1, 2, 3), dm.getCollections().stream().map(BookCollection::getOrder).toList());
    }

    @Test
    void formatExclusivityOfCollections() {
        DataManager dm = new DataManager(file());
        BookCollection empty = new BookCollection("Empty");
        BookCollection epubs = new BookCollection("Novels");
        BookCollection pdfs = new BookCollection("Manga");
        BookCollection mixed = new BookCollection("Mixed");
        for (BookCollection x : List.of(empty, epubs, pdfs, mixed)) dm.addCollection(x);
        dm.addBooks(List.of(new Book(epubs.getId(), "N1", "n1.epub", 1), new Book(epubs.getId(), "N2", "n2.EPUB", 2)));
        dm.addBooks(List.of(new Book(pdfs.getId(), "M1", "m1.pdf", 1)));
        dm.addBooks(List.of(new Book(mixed.getId(), "X1", "x1.epub", 1), new Book(mixed.getId(), "X2", "x2.PDF", 2)));

        assertFalse(dm.hasOnlyFormat(empty, Book.FORMAT_EPUB));
        assertFalse(dm.hasOnlyFormat(empty, Book.FORMAT_PDF));
        assertTrue(dm.hasOnlyFormat(epubs, Book.FORMAT_EPUB));
        assertFalse(dm.hasOnlyFormat(epubs, Book.FORMAT_PDF));
        assertTrue(dm.hasOnlyFormat(pdfs, Book.FORMAT_PDF));
        assertFalse(dm.hasOnlyFormat(pdfs, Book.FORMAT_EPUB));
        assertFalse(dm.hasOnlyFormat(mixed, Book.FORMAT_EPUB));
        assertFalse(dm.hasOnlyFormat(mixed, Book.FORMAT_PDF));

        DataManager reloaded = new DataManager(file());
        assertEquals(Book.FORMAT_PDF, reloaded.getBooksOf(pdfs.getId()).get(0).getFormat(), "format is persisted");
    }

    private static List<String> collectionTitles(DataManager dm) {
        return dm.getCollections().stream().map(BookCollection::getTitle).toList();
    }

    // ------------------------------------------------------------------
    // Importing folders
    // ------------------------------------------------------------------

    @Test
    void importingAFolderCreatesTheCollectionAndImportingItAgainOnlyAddsWhatIsNew() {
        DataManager dm = new DataManager(file());
        dm.addCollection(new BookCollection("Other"));
        Path v1 = folder.resolve("Series/Vol 1.epub");
        Path v2 = folder.resolve("Series/Vol 2.epub");
        Path v3 = folder.resolve("Series/Extras/Vol 3.pdf");

        DataManager.FolderImport first = dm.importFolder("Series", List.of(v1, v2));
        assertTrue(first.created());
        assertEquals("Series", first.collection().getTitle());
        assertEquals(List.of("Vol 1", "Vol 2"), first.added().stream().map(Book::getTitle).toList());
        assertEquals(0, first.alreadyThere());
        assertEquals(List.of("Other", "Series"), collectionTitles(dm), "the new collection goes last");
        assertEquals(v1.toAbsolutePath().normalize().toString(), first.added().get(0).getFilePath());
        assertEquals(Book.FORMAT_EPUB, first.added().get(0).getFormat());

        // The same folder again, with a new volume in a subfolder: matched by title, ignoring case.
        DataManager.FolderImport again = dm.importFolder("series", List.of(v1, v2, v3, v3));
        assertFalse(again.created());
        assertEquals(first.collection().getId(), again.collection().getId());
        assertEquals(List.of("Vol 3"), again.added().stream().map(Book::getTitle).toList());
        assertEquals(3, again.alreadyThere(), "two known files and one repeated in the batch");
        assertEquals(Book.FORMAT_PDF, again.added().get(0).getFormat());
        assertEquals(List.of("Vol 1", "Vol 2", "Vol 3"), titles(dm, first.collection()));
        assertEquals(List.of(1, 2, 3), dm.getBooksOf(first.collection().getId()).stream().map(Book::getOrder).toList());
        assertEquals(2, dm.getCollections().size(), "no second \"Series\" collection");

        DataManager reloaded = new DataManager(file());
        assertEquals(List.of("Vol 1", "Vol 2", "Vol 3"), titles(reloaded, first.collection()), "the import is saved");
        assertTrue(reloaded.findCollectionByTitle("  SERIES ").isPresent());
        assertTrue(reloaded.findCollectionByTitle("Series 2").isEmpty());
    }

    @Test
    void addVolumesAppendsAfterTheExistingOnesAndSkipsDuplicates() {
        DataManager dm = new DataManager(file());
        BookCollection c = new BookCollection("Manga");
        dm.addCollection(c);
        Path a = folder.resolve("a.pdf");
        Path b = folder.resolve("b.pdf");
        dm.addBook(new Book(c.getId(), "A", a.toString(), 1));

        List<Book> added = dm.addVolumes(c, List.of(folder.resolve("./a.pdf"), b, b));
        assertEquals(List.of("b"), added.stream().map(Book::getTitle).toList());
        assertEquals(2, added.get(0).getOrder());
        assertEquals(List.of("A", "b"), titles(dm, c));
        assertTrue(dm.addVolumes(c, List.of(a, b)).isEmpty(), "nothing new: nothing added");
        assertEquals(List.of("A", "b"), titles(new DataManager(file()), c));
    }

    private static List<String> titles(DataManager dm, BookCollection c) {
        return dm.getBooksOf(c.getId()).stream().map(Book::getTitle).toList();
    }

    @Test
    void bookmarksArePersistedAndOlderFilesLoadWithoutThem() throws IOException {
        DataManager dm = new DataManager(file());
        BookCollection c = new BookCollection("Novels");
        dm.addCollection(c);
        Book book = new Book(c.getId(), "Vol 1", "C:/x/1.epub", 1);
        dm.addBook(book);
        Bookmark first = new Bookmark(2, 0.25, "The ferry left before the mist had lifted");
        first.setNote("start of the journey");
        book.addBookmark(new Bookmark(7, 0.0, "later"));
        book.addBookmark(first);
        dm.save();

        Book reloaded = new DataManager(file()).getBooksOf(c.getId()).get(0);
        assertEquals(2, reloaded.getBookmarks().size());
        Bookmark b = reloaded.getBookmarks().get(0);
        assertEquals(2, b.getChapter());
        assertEquals(0.25, b.getFraction(), 1e-9);
        assertEquals("The ferry left before the mist had lifted", b.getExcerpt());
        assertEquals("start of the journey", b.getNote());
        assertEquals(first.getId(), b.getId());
        assertEquals(7, reloaded.getBookmarks().get(1).getChapter());

        // A file written before bookmarks existed has no "bookmarks" field at all.
        Files.writeString(file(), """
                { "collections": [ { "id": "c1", "title": "Old" } ],
                  "books": [ { "id": "b1", "collectionId": "c1", "title": "Vol", "filePath": "C:/x/v.epub", "order": 1 } ] }
                """, StandardCharsets.UTF_8);
        Book old = new DataManager(file()).getBooksOf("c1").get(0);
        assertTrue(old.getBookmarks().isEmpty());
        old.addBookmark(new Bookmark(0, 0.0, ""));
        assertEquals(1, old.getBookmarks().size(), "bookmarks can be added to books from older files");
    }
}
