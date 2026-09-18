package com.devavaxp.reader;

import com.devavaxp.reader.data.DataManager;
import com.devavaxp.reader.epub.EpubExtractor;
import com.devavaxp.reader.model.Preferences;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * JavaFX entry point of Devava Reader. Creates the window, the single scene and the
 * screen navigator.
 */
public class ReaderApp extends Application {

    public static final String APP_NAME = "Devava Reader";

    private DataManager dataManager;
    private Navigator navigator;

    @Override
    public void start(Stage stage) {
        // Leftovers of previous sessions (books extracted into the temp folder).
        Thread cleanup = new Thread(EpubExtractor::clearCache, "cache-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();

        dataManager = new DataManager();
        dataManager.saveIfMissing(); // the library file exists from the very first run
        Preferences prefs = dataManager.getPreferences();

        Scene scene = new Scene(new StackPane(), prefs.getWindowWidth(), prefs.getWindowHeight());
        scene.getStylesheets().add(ReaderApp.class.getResource("styles.css").toExternalForm());

        navigator = new Navigator(stage, scene, dataManager, getHostServices());

        stage.setTitle(APP_NAME);
        stage.getIcons().add(createIcon());
        stage.setScene(scene);
        stage.setMaximized(prefs.isWindowMaximized());
        stage.setOnCloseRequest(e -> close(stage));

        navigator.showCollections();
        stage.show();
        // After show(): on Linux (GTK) a minimum size set before showing replaces the initial
        // scene size, and the window would open at 720x480 instead of the saved size.
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        if (!stage.isMaximized()) {
            Platform.runLater(() -> restoreSize(stage, prefs.getWindowWidth(), prefs.getWindowHeight()));
        }
    }

    /**
     * Some window managers (seen with GTK on X11) map the window at a size other than the one
     * the scene asked for. Once the window is mapped a resize request is honoured, so re-apply
     * the saved size — keeping whatever the decorations add — when it was not respected.
     */
    private static void restoreSize(Stage stage, double sceneWidth, double sceneHeight) {
        Scene scene = stage.getScene();
        if (scene == null || stage.isMaximized() || stage.isFullScreen()) return;
        double decorationWidth = Math.max(0, stage.getWidth() - scene.getWidth());
        double decorationHeight = Math.max(0, stage.getHeight() - scene.getHeight());
        if (Math.abs(scene.getWidth() - sceneWidth) > 2 || Math.abs(scene.getHeight() - sceneHeight) > 2) {
            stage.setWidth(sceneWidth + decorationWidth);
            stage.setHeight(sceneHeight + decorationHeight);
        }
    }

    private void close(Stage stage) {
        navigator.onApplicationClose();
        Preferences prefs = dataManager.getPreferences();
        prefs.setWindowMaximized(stage.isMaximized());
        if (!stage.isMaximized() && !stage.isFullScreen()) {
            prefs.setWindowWidth(stage.getScene().getWidth());
            prefs.setWindowHeight(stage.getScene().getHeight());
        }
        dataManager.save();
        EpubExtractor.clearCache();
    }

    @Override
    public void stop() {
        if (dataManager != null) {
            dataManager.save();
        }
    }

    // ------------------------------------------------------------------
    // Window icon (generated at runtime: an open book on a rounded square)
    // ------------------------------------------------------------------

    private static Image createIcon() {
        int n = 64;
        WritableImage img = new WritableImage(n, n);
        PixelWriter pw = img.getPixelWriter();
        Color background = Color.web("#4F46E5");
        Color page = Color.web("#FFFFFF");
        Color line = Color.web("#C7D2FE");
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                Color c = Color.TRANSPARENT;
                if (insideRoundedSquare(x, y, n, 14)) {
                    c = background;
                    boolean leftPage = x >= 14 && x <= 30 && y >= 18 && y <= 46;
                    boolean rightPage = x >= 34 && x <= 50 && y >= 18 && y <= 46;
                    if (leftPage || rightPage) {
                        c = page;
                        boolean isLine = (y - 22) % 6 == 0 && y >= 22 && y <= 42
                                && ((leftPage && x >= 17 && x <= 27) || (rightPage && x >= 37 && x <= 47));
                        if (isLine) c = line;
                    }
                }
                pw.setColor(x, y, c);
            }
        }
        return img;
    }

    private static boolean insideRoundedSquare(int x, int y, int n, int radius) {
        int cx = x < radius ? radius : (x >= n - radius ? n - radius - 1 : x);
        int cy = y < radius ? radius : (y >= n - radius ? n - radius - 1 : y);
        int dx = x - cx;
        int dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
