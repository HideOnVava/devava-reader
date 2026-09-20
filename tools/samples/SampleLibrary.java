import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Creates a complete sample library in a folder: three EPUB novels, three PDF manga volumes
 * (see SampleEpubGen and MangaPdfGen) and a library.json that already knows them, with some
 * reading progress so that every screen has something to show. Used by the smoke tests and
 * for screenshots:
 *
 *   javac -cp <pdfbox jars> -d out tools/samples/*.java
 *   java  -cp out:<pdfbox jars> SampleLibrary <folder>
 *   ... then run the app with -Dreader.library=<folder>/library.json
 *
 * The novel "The Lantern Road, Vol. 2" is the most recently read book, so the "Continue
 * reading" card opens the EPUB reader; "Sample Manga, Vol. 1" is the second most recent.
 * A subfolder "Lantern Import" holds copies of the three novels and is not in the library:
 * the smoke tests import it as a collection.
 */
public class SampleLibrary {

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "samples").getAbsoluteFile();
        dir.mkdirs();
        SampleEpubGen.main(new String[]{dir.getPath()});
        MangaPdfGen.main(new String[]{dir.getPath()});
        File importFolder = new File(dir, "Lantern Import");
        importFolder.mkdirs();
        for (int v = 1; v <= 3; v++) {
            String name = "The Lantern Road Vol. " + v + ".epub";
            Files.copy(new File(dir, name).toPath(), new File(importFolder, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        long now = System.currentTimeMillis();
        String novel = "aaaaaaaa-0000-0000-0000-000000000001";
        String manga = "aaaaaaaa-0000-0000-0000-000000000002";
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"collections\": [\n");
        json.append("    {\"id\": \"").append(novel).append("\", \"title\": \"The Lantern Road\", \"order\": 1, \"pinned\": true},\n");
        json.append("    {\"id\": \"").append(manga).append("\", \"title\": \"Sample Manga\", \"order\": 2, \"pinned\": false}\n");
        json.append("  ],\n  \"books\": [\n");
        json.append(book(dir, novel, "The Lantern Road, Vol. 1", "The Lantern Road Vol. 1.epub", 1, 100.0, true, "6:1.00000", now - 3 * 86_400_000L, "epub")).append(",\n");
        json.append(book(dir, novel, "The Lantern Road, Vol. 2", "The Lantern Road Vol. 2.epub", 2, 38.0, false, "3:0.25000", now, "epub")).append(",\n");
        json.append(book(dir, novel, "The Lantern Road, Vol. 3", "The Lantern Road Vol. 3.epub", 3, 0.0, false, "", 0, "epub")).append(",\n");
        json.append(book(dir, manga, "Sample Manga, Vol. 1", "Sample Manga v01.pdf", 1, 30.0, false, "11:0.00000", now - 3_600_000L, "pdf")).append(",\n");
        json.append(book(dir, manga, "Sample Manga, Vol. 2", "Sample Manga v02.pdf", 2, 0.0, false, "", 0, "pdf")).append(",\n");
        json.append(book(dir, manga, "Sample Manga, Vol. 3", "Sample Manga v03.pdf", 3, 0.0, false, "", 0, "pdf")).append("\n");
        json.append("  ],\n  \"preferences\": {\n");
        json.append("    \"fontSize\": 20, \"theme\": \"light\", \"typeface\": \"serif\", \"columns\": 2,\n");
        json.append("    \"windowWidth\": 1000.0, \"windowHeight\": 680.0, \"windowMaximized\": false, \"lastFolder\": \"\",\n");
        json.append("    \"pdfLayout\": \"double\", \"pdfDirection\": \"rtl\", \"pdfFit\": \"page\"\n");
        json.append("  }\n}\n");

        Path library = dir.toPath().resolve("library.json");
        Files.writeString(library, json.toString(), StandardCharsets.UTF_8);
        System.out.println("Library written to " + library);
    }

    private static String book(File dir, String collection, String title, String file, int order, double percentage,
                               boolean read, String position, long lastReadAt, String format) {
        String path = new File(dir, file).getPath();
        return "    {\"id\": \"" + UUID.randomUUID() + "\", \"collectionId\": \"" + collection + "\", \"title\": \"" + title
                + "\", \"filePath\": " + quote(path) + ", \"order\": " + order + ", \"readingPercentage\": " + percentage
                + ", \"read\": " + read + ", \"savedPosition\": \"" + position + "\", \"lastReadAt\": " + lastReadAt
                + ", \"format\": \"" + format + "\"}";
    }

    /** JSON string literal (paths on Windows contain backslashes). */
    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
