package com.devavaxp.reader.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * The few places where the application depends on the operating system: where the user's
 * documents and the library live, and how the shortcut modifier key is called.
 * <ul>
 *   <li><b>Windows</b>: the real Documents folder as reported by the shell (it may be
 *       redirected, for example to OneDrive), then {@code Devava Reader}.</li>
 *   <li><b>macOS</b>: {@code ~/Documents/Devava Reader}.</li>
 *   <li><b>Linux</b>: the XDG Documents folder ({@code XDG_DOCUMENTS_DIR} in
 *       {@code ~/.config/user-dirs.dirs}, localized on most desktops), then
 *       {@code Devava Reader}; on systems without such a folder,
 *       {@code $XDG_DATA_HOME/devava-reader} ({@code ~/.local/share/devava-reader}).</li>
 * </ul>
 */
public final class AppDirectories {

    /** Name of the folder that holds the library, next to the user's documents. */
    public static final String LIBRARY_FOLDER = "Devava Reader";

    private AppDirectories() {
    }

    public static boolean isWindows() {
        return osName().startsWith("windows");
    }

    public static boolean isMac() {
        return osName().contains("mac") || osName().contains("darwin");
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }

    /** Human name of the shortcut modifier: {@code ⌘} on macOS, {@code Ctrl} elsewhere. */
    public static String shortcutKey() {
        return isMac() ? "⌘" : "Ctrl";
    }

    /** Folder that holds {@code library.json} (created on demand by the data manager). */
    public static Path libraryFolder() {
        Path documents = documentsFolder();
        if (documents != null) {
            return documents.resolve(LIBRARY_FOLDER);
        }
        // Linux without a Documents folder: follow the XDG base directory convention.
        String dataHome = System.getenv("XDG_DATA_HOME");
        Path base = dataHome != null && !dataHome.isBlank()
                ? Paths.get(dataHome)
                : home().resolve(".local").resolve("share");
        return base.resolve("devava-reader");
    }

    /**
     * The user's Documents folder, or {@code null} when the system has none (possible on
     * Linux servers or minimal desktops).
     */
    public static Path documentsFolder() {
        Path home = home();
        if (isWindows()) {
            Path shell = windowsDocuments();
            if (shell != null) return shell;
        } else if (!isMac()) {
            Path xdg = xdgDocuments(home);
            if (xdg != null) return xdg;
        }
        Path documents = home.resolve("Documents");
        if (Files.isDirectory(documents)) return documents;
        return isWindows() || isMac() ? home : null;
    }

    private static Path home() {
        return Paths.get(System.getProperty("user.home"));
    }

    /** Asks the Windows shell for the "Personal" folder, which honors redirection (OneDrive). */
    private static Path windowsDocuments() {
        try {
            java.io.File dir = javax.swing.filechooser.FileSystemView.getFileSystemView().getDefaultDirectory();
            if (dir != null && dir.isDirectory()) {
                return dir.toPath().toAbsolutePath().normalize();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Headless or unsupported: fall back to ~/Documents.
        }
        return null;
    }

    /** Reads {@code XDG_DOCUMENTS_DIR="$HOME/Documentos"} from {@code ~/.config/user-dirs.dirs}. */
    private static Path xdgDocuments(Path home) {
        String configHome = System.getenv("XDG_CONFIG_HOME");
        Path config = (configHome != null && !configHome.isBlank() ? Paths.get(configHome) : home.resolve(".config"))
                .resolve("user-dirs.dirs");
        return xdgDocuments(home, config);
    }

    /** The Documents folder declared in the given {@code user-dirs.dirs} file, if it exists. */
    static Path xdgDocuments(Path home, Path config) {
        if (!Files.isRegularFile(config)) return null;
        try {
            for (String line : Files.readAllLines(config, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("XDG_DOCUMENTS_DIR=")) continue;
                String value = trimmed.substring("XDG_DOCUMENTS_DIR=".length()).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                value = value.replace("$HOME", home.toString()).replace("${HOME}", home.toString());
                Path dir = Paths.get(value);
                if (Files.isDirectory(dir) && !dir.equals(home)) return dir;
            }
        } catch (IOException | RuntimeException ignored) {
            // Unreadable or malformed: behave as if the file did not exist.
        }
        return null;
    }
}
