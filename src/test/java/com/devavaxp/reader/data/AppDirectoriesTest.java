package com.devavaxp.reader.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppDirectoriesTest {

    @TempDir
    Path home;

    @Test
    void libraryFolderIsNamedAfterTheApp() {
        Path folder = AppDirectories.libraryFolder();
        assertTrue(folder.isAbsolute());
        String name = folder.getFileName().toString();
        assertTrue(name.equals(AppDirectories.LIBRARY_FOLDER) || name.equals("devava-reader"), name);
        if (AppDirectories.isWindows() || AppDirectories.isMac()) {
            assertEquals(AppDirectories.LIBRARY_FOLDER, name);
            assertTrue(Files.isDirectory(AppDirectories.documentsFolder()), "Documents (or home) must exist");
        }
    }

    @Test
    void shortcutKeyFollowsThePlatform() {
        assertEquals(AppDirectories.isMac() ? "⌘" : "Ctrl", AppDirectories.shortcutKey());
        assertFalse(AppDirectories.isWindows() && AppDirectories.isMac());
    }

    @Test
    void readsTheXdgDocumentsFolder() throws Exception {
        Path config = home.resolve("user-dirs.dirs");
        Path documentos = Files.createDirectory(home.resolve("Documentos"));
        Files.writeString(config, """
                # This file is written by xdg-user-dirs-update
                XDG_DESKTOP_DIR="$HOME/Escritorio"
                XDG_DOCUMENTS_DIR="$HOME/Documentos"
                XDG_DOWNLOAD_DIR="$HOME/Descargas"
                """, StandardCharsets.UTF_8);
        assertEquals(documentos, AppDirectories.xdgDocuments(home, config));

        Files.writeString(config, "XDG_DOCUMENTS_DIR=\"$HOME/Missing\"\n", StandardCharsets.UTF_8);
        assertNull(AppDirectories.xdgDocuments(home, config), "a declared folder that does not exist is ignored");

        Files.writeString(config, "XDG_DOCUMENTS_DIR=\"$HOME\"\n", StandardCharsets.UTF_8);
        assertNull(AppDirectories.xdgDocuments(home, config), "a Documents folder equal to home means none");

        assertNull(AppDirectories.xdgDocuments(home, home.resolve("absent.dirs")));
    }
}
