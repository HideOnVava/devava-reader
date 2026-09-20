package com.devavaxp.reader.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookFolderTest {

    @TempDir
    Path folder;

    private Path touch(String relative) throws IOException {
        Path p = folder.resolve(relative);
        Files.createDirectories(p.getParent());
        return Files.writeString(p, "x");
    }

    private static List<String> names(BookFolder.Scan scan, Path root) {
        List<String> out = new ArrayList<>();
        for (Path p : scan.books()) out.add(root.relativize(p).toString().replace('\\', '/'));
        return out;
    }

    @Test
    void booksOfAFolderComeInShelfOrderAndOtherFilesAreIgnored() throws IOException {
        touch("Volume 10.epub");
        touch("Volume 2.epub");
        touch("volume 1.EPUB");
        touch("notes.txt");
        touch("cover.jpg");
        touch("Extras/Side story 2.pdf");
        touch("Extras/Side story 1.pdf");
        touch("Extras/Art/Sketches.pdf");
        touch("Audio drama/track.mp3");
        touch(".hidden/secret.epub");
        touch("Extras/.DS_Store");
        touch("Extras/._Side story 1.pdf");
        Files.createDirectories(folder.resolve("Fake.epub"));   // a folder with a book-like name

        BookFolder.Scan scan = BookFolder.scan(folder);
        assertTrue(scan.complete());
        assertEquals(List.of("volume 1.EPUB", "Volume 2.epub", "Volume 10.epub",
                        "Extras/Side story 1.pdf", "Extras/Side story 2.pdf", "Extras/Art/Sketches.pdf"),
                names(scan, folder.toAbsolutePath().normalize()),
                "files of a folder first in natural order, then each subfolder, recursively");
        assertTrue(scan.books().stream().allMatch(Path::isAbsolute));
    }

    @Test
    void depthAndSizeLimits() throws IOException {
        touch("a.epub");
        touch("deep/b.epub");
        touch("deep/deeper/c.epub");

        BookFolder.Scan shallow = BookFolder.scan(folder, 1, BookFolder.MAX_ENTRIES);
        assertEquals(List.of("a.epub"), names(shallow, folder.toAbsolutePath().normalize()));
        assertTrue(shallow.complete(), "a depth limit is not a failure");

        BookFolder.Scan aborted = BookFolder.scan(folder, BookFolder.MAX_DEPTH, 3);
        assertFalse(aborted.complete(), "too many entries: the scan is abandoned");

        BookFolder.Scan missing = BookFolder.scan(folder.resolve("does-not-exist"));
        assertTrue(missing.books().isEmpty());
        assertTrue(missing.complete());
    }

    @Test
    void collectionNameAndBookExtensions() {
        assertEquals("Mushoku Tensei", BookFolder.nameOf(Paths.get("D:/Books/Mushoku Tensei")));
        assertEquals("Mushoku Tensei", BookFolder.nameOf(Paths.get("D:/Books/Mushoku Tensei/")));
        assertFalse(BookFolder.nameOf(Paths.get("/")).isEmpty(), "even a root gets some name");

        assertTrue(BookFolder.isBook(Paths.get("a/b/Volume 1.EPUB")));
        assertTrue(BookFolder.isBook(Paths.get("Chapter.pdf")));
        assertFalse(BookFolder.isBook(Paths.get("Volume 1.epub.part")));
        assertFalse(BookFolder.isBook(Paths.get("notes.txt")));
        assertFalse(BookFolder.isBook(null));
    }

    @Test
    void importOrderAcrossFolders() {
        List<Path> paths = new ArrayList<>(List.of(
                Paths.get("/lib/Series B/Vol 1.epub"),
                Paths.get("/lib/Series A/Extras/SS 1.epub"),
                Paths.get("/lib/Series A/Vol 10.epub"),
                Paths.get("/lib/Series A/Vol 2.epub"),
                Paths.get("/lib/loose.pdf")));
        paths.sort(BookFolder::compareImportOrder);
        assertEquals(List.of(
                Paths.get("/lib/loose.pdf"),
                Paths.get("/lib/Series A/Vol 2.epub"),
                Paths.get("/lib/Series A/Vol 10.epub"),
                Paths.get("/lib/Series A/Extras/SS 1.epub"),
                Paths.get("/lib/Series B/Vol 1.epub")), paths);
    }
}
