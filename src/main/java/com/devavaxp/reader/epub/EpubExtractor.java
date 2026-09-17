package com.devavaxp.reader.epub;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts an .epub file (a ZIP archive) into a temporary cache folder so that the WebView
 * can load its pages, images, fonts and stylesheets through {@code file:} URLs.
 */
public final class EpubExtractor {

    private EpubExtractor() {
    }

    /** Root of the cache: {@code <system temp>/DevavaReader}. */
    public static Path cacheRoot() {
        return Paths.get(System.getProperty("java.io.tmpdir"), "DevavaReader");
    }

    /**
     * Extracts the EPUB into {@code <cache>/<bookId>} (removing any previous leftovers)
     * and returns that folder.
     */
    public static Path extract(Path epub, String bookId) throws IOException {
        if (!Files.isRegularFile(epub)) {
            throw new IOException("File not found: " + epub);
        }
        Path target = cacheRoot().resolve(safeName(bookId)).toAbsolutePath().normalize();
        deleteDirectory(target);
        Files.createDirectories(target);

        try (ZipFile zip = new ZipFile(epub.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                try {
                    Path out = target.resolve(name).normalize();
                    // "Zip slip" protection: no entry may escape the target folder.
                    if (!out.startsWith(target)) continue;
                    Files.createDirectories(out.getParent());
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException | InvalidPathException e) {
                    // An entry whose name is invalid on Windows must not prevent reading the rest of the book.
                    System.err.println("Could not extract '" + name + "': " + e.getMessage());
                }
            }
        }
        return target;
    }

    /** Deletes a folder and everything inside it. Individual failures are ignored. */
    public static void deleteDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // File in use or another transient problem: ignored.
                }
            });
        } catch (IOException ignored) {
            // The folder no longer exists or cannot be walked.
        }
    }

    /** Removes the whole cache (leftovers of previous sessions). */
    public static void clearCache() {
        deleteDirectory(cacheRoot());
    }

    private static String safeName(String id) {
        String clean = id == null ? "" : id.replaceAll("[^A-Za-z0-9._-]", "_");
        return clean.isEmpty() ? "book" : clean;
    }
}
