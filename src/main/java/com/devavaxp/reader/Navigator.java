package com.devavaxp.reader;

import com.devavaxp.reader.data.DataManager;
import com.devavaxp.reader.model.Book;
import com.devavaxp.reader.model.BookCollection;
import javafx.application.HostServices;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Switches between the application screens reusing a single {@link Scene}, so the window
 * size, the stylesheet and the maximized state are preserved while navigating.
 */
public final class Navigator {

    /** Controllers that need to release resources when they are left implement this. */
    public interface Screen {
        default void onLeave() {
        }
    }

    private final Stage stage;
    private final Scene scene;
    private final DataManager dataManager;
    private final HostServices hostServices;
    private Object currentController;

    public Navigator(Stage stage, Scene scene, DataManager dataManager, HostServices hostServices) {
        this.stage = stage;
        this.scene = scene;
        this.dataManager = dataManager;
        this.hostServices = hostServices;
    }

    public Stage getStage() {
        return stage;
    }

    public Scene getScene() {
        return scene;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public HostServices getHostServices() {
        return hostServices;
    }

    // ------------------------------------------------------------------
    // Screens
    // ------------------------------------------------------------------

    public void showCollections() {
        load("collections-view.fxml", (CollectionsController c) -> c.init(this));
    }

    public void showVolumes(BookCollection collection) {
        load("volumes-view.fxml", (VolumesController c) -> c.init(this, collection));
    }

    public void showReader(Book book) {
        load("reader-view.fxml", (ReaderController c) -> c.init(this, book));
    }

    /** Tells the current screen that the application is closing (to save progress, etc.). */
    public void onApplicationClose() {
        leaveCurrent();
    }

    private <T> void load(String fxml, Consumer<T> initializer) {
        try {
            FXMLLoader loader = new FXMLLoader(Navigator.class.getResource(fxml));
            Parent root = loader.load();
            T controller = loader.getController();
            leaveCurrent();
            currentController = controller;
            scene.setRoot(root);
            initializer.accept(controller);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load screen " + fxml, e);
        }
    }

    private void leaveCurrent() {
        if (currentController instanceof Screen s) {
            try {
                s.onLeave();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        currentController = null;
    }
}
