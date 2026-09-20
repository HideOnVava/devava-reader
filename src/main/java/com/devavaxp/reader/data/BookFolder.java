package com.devavaxp.reader.data;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Finds the book files of a folder so that the folder can be imported as a collection.
 * <p>
 * Books are listed in the order a reader expects on the shelf: the files directly inside
 * the folder first, in natural order ("Volume 2" before "Volume 10"), then the contents of
 * each subfolder, subfolders also in natural order, recursively. Hidden entries and names
 * starting with a dot are ignored, symbolic links are not followed, and the walk gives up
 * on folders far too large to be a series (see {@link Scan#complete()}).
 */
public final class BookFolder {

    /** How many levels of subfolders are looked into. */
    public static final int MAX_DEPTH = 8;
    /** Files and folders visited before a scan is abandoned as "not a folder of books". */
    public static final int MAX_ENTRIES = 20_000;

    /**
     * Books found in a folder, in import order. {@code complete} is false when the walk
     * stopped early because the folder holds more than {@link #MAX_ENTRIES} entries: the
     * list is then partial and should not be imported.
     */
    public record Scan(List<Path> books, boolean complete) {
    }

    private BookFolder() {
    }

    /** Whether the file name has one of the extensions the app can open (.epub, .pdf). */
    public static boolean isBook(Path file) {
        Path name = file == null ? null : file.getFileName();
        if (name == null) return false;
        String n = name.toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".epub") || n.endsWith(".pdf");
    }

    /** Name for the collection made from a folder: the folder's own name. */
    public static String nameOf(Path folder) {
        Path absolute = folder.toAbsolutePath().normalize();
        Path name = absolute.getFileName();
        // The root of a drive ("D:\") has no file name of its own.
        String s = (name == null ? absolute.toString() : name.toString()).replaceAll("[\\\\/:]+$", "").trim();
        return s.isEmpty() ? absolute.toString() : s;
    }

    /** Lists the books of the folder (see the class description). */
    public static Scan scan(Path folder) {
        return scan(folder, MAX_DEPTH, MAX_ENTRIES);
    }

    static Scan scan(Path folder, int maxDepth, int maxEntries) {
        Path root = folder.toAbsolutePath().normalize();
        List<Path> books = new ArrayList<>();
        int[] visited = {0};
        boolean[] complete = {true};
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(root) && isHidden(dir, attrs)) return FileVisitResult.SKIP_SUBTREE;
                    return countEntry();
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // Without FOLLOW_LINKS a symbolic link arrives here and is not a regular file.
                    if (attrs.isRegularFile() && isBook(file) && !isHidden(file, attrs)) books.add(file);
                    return countEntry();
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;   // unreadable entries are simply skipped
                }

                private FileVisitResult countEntry() {
                    if (++visited[0] > maxEntries) {
                        complete[0] = false;
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // The folder itself could not be read: nothing found.
        }
        books.sort(BookFolder::compareImportOrder);
        return new Scan(List.copyOf(books), complete[0]);
    }

    private static boolean isHidden(Path path, BasicFileAttributes attrs) {
        Path name = path.getFileName();
        if (name != null && name.toString().startsWith(".")) return true;
        return attrs instanceof DosFileAttributes dos && dos.isHidden();
    }

    /**
     * Shelf order of book paths: inside a folder its files come first, in natural order,
     * then its subfolders in natural order, each one expanded the same way. Paths should be
     * absolute and normalized (or all relative to the same folder) to be comparable.
     */
    public static int compareImportOrder(Path a, Path b) {
        int na = a.getNameCount();
        int nb = b.getNameCount();
        int common = Math.min(na, nb);
        for (int i = 0; i < common; i++) {
            boolean aIsFile = i == na - 1;
            boolean bIsFile = i == nb - 1;
            if (aIsFile != bIsFile) return aIsFile ? -1 : 1;   // files before subfolders
            int cmp = TextUtils.compareNatural(a.getName(i).toString(), b.getName(i).toString());
            if (cmp != 0) return cmp;
        }
        return Integer.compare(na, nb);
    }
}
