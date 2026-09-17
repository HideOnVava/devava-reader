package com.devavaxp.reader.data;

import com.devavaxp.reader.model.Book;
import com.devavaxp.reader.model.BookCollection;
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

    private static List<String> titles(DataManager dm, BookCollection c) {
        return dm.getBooksOf(c.getId()).stream().map(Book::getTitle).toList();
    }
}
