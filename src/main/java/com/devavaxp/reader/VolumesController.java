package com.devavaxp.reader;

import com.devavaxp.reader.data.AppDirectories;
import com.devavaxp.reader.data.DataManager;
import com.devavaxp.reader.data.TextUtils;
import com.devavaxp.reader.model.Book;
import com.devavaxp.reader.model.BookCollection;
import com.devavaxp.reader.model.Preferences;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Collection screen: import volumes, reorder them, mark them and open the reader.
 */
public class VolumesController implements Navigator.Screen {

    @FXML private Label collectionTitle;
    @FXML private Label summaryLabel;
    @FXML private ListView<Book> bookList;
    @FXML private Button moveUpButton;
    @FXML private Button moveDownButton;
    @FXML private Button renameButton;
    @FXML private Button readButton;
    @FXML private Button removeButton;
    @FXML private Button openReaderButton;

    private Navigator navigator;
    private DataManager dataManager;
    private BookCollection collection;
    private ObservableList<Book> books;

    // ------------------------------------------------------------------
    // Initialization
    // ------------------------------------------------------------------

    public void init(Navigator navigator, BookCollection collection) {
        this.navigator = navigator;
        this.dataManager = navigator.getDataManager();
        this.collection = collection;

        bookList.setCellFactory(list -> new BookCell());
        bookList.setPlaceholder(new Label("No volumes yet. Add .epub or .pdf files, or drop them here."));
        bookList.getSelectionModel().selectedItemProperty().addListener((obs, a, b) -> updateButtons());
        bookList.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2) openSelectedInReader();
        });
        bookList.setOnKeyPressed(this::onListKey);
        bookList.setContextMenu(createContextMenu());
        String mod = AppDirectories.shortcutKey();
        moveUpButton.setTooltip(new Tooltip("Move up (" + mod + "+↑)"));
        moveDownButton.setTooltip(new Tooltip("Move down (" + mod + "+↓)"));
        bookList.setOnDragOver(this::onDragOver);
        bookList.setOnDragDropped(this::onDragDropped);

        collectionTitle.setText(collection.getTitle());
        reload();
        bookList.getSelectionModel().selectFirst();
        bookList.requestFocus();

        navigator.getScene().getRoot().addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                onBack();
                ev.consume();
            }
        });
    }

    private ContextMenu createContextMenu() {
        MenuItem read = new MenuItem("Read");
        read.setOnAction(e -> openSelectedInReader());
        MenuItem rename = new MenuItem("Rename…");
        rename.setOnAction(e -> onRename());
        MenuItem toggleRead = new MenuItem("Mark as read / unread");
        toggleRead.setOnAction(e -> onToggleRead());
        MenuItem reset = new MenuItem("Reset progress");
        reset.setOnAction(e -> resetProgress());
        MenuItem locate = new MenuItem("Locate file…");
        locate.setOnAction(e -> locateFile());
        MenuItem remove = new MenuItem("Remove from collection…");
        remove.getStyleClass().add("danger");
        remove.setOnAction(e -> onRemove());
        return new ContextMenu(read, rename, toggleRead, reset, new SeparatorMenuItem(), locate,
                new SeparatorMenuItem(), remove);
    }

    private void reload() {
        Book selected = bookList.getSelectionModel().getSelectedItem();
        books = FXCollections.observableArrayList(dataManager.getBooksOf(collection.getId()));
        bookList.setItems(books);
        if (selected != null) bookList.getSelectionModel().select(selected);

        int total = books.size();
        int read = (int) books.stream().filter(Book::isRead).count();
        summaryLabel.setText(total == 0 ? "No volumes yet"
                : TextUtils.plural(total, "volume", "volumes") + " · " + read + " read");
        updateButtons();
    }

    private void updateButtons() {
        Book selected = bookList.getSelectionModel().getSelectedItem();
        int index = bookList.getSelectionModel().getSelectedIndex();
        boolean any = selected != null;
        moveUpButton.setDisable(!any || index <= 0);
        moveDownButton.setDisable(!any || index >= books.size() - 1);
        renameButton.setDisable(!any);
        readButton.setDisable(!any);
        removeButton.setDisable(!any);
        openReaderButton.setDisable(!any || !fileExists(selected));
        readButton.setText(any && selected.isRead() ? "Mark as unread" : "Mark as read");
    }

    private static boolean fileExists(Book book) {
        return book != null && book.getFilePath() != null && Files.isRegularFile(Paths.get(book.getFilePath()));
    }

    // ------------------------------------------------------------------
    // Keyboard and drag & drop
    // ------------------------------------------------------------------

    private void onListKey(KeyEvent ev) {
        switch (ev.getCode()) {
            case ENTER -> { openSelectedInReader(); ev.consume(); }
            case DELETE -> { onRemove(); ev.consume(); }
            case F2 -> { onRename(); ev.consume(); }
            case UP -> { if (ev.isShortcutDown()) { onMoveUp(); ev.consume(); } }
            case DOWN -> { if (ev.isShortcutDown()) { onMoveDown(); ev.consume(); } }
            default -> { }
        }
    }

    private void onDragOver(DragEvent ev) {
        if (ev.getDragboard().hasFiles() && ev.getDragboard().getFiles().stream().anyMatch(VolumesController::isSupportedBook)) {
            ev.acceptTransferModes(TransferMode.COPY);
        }
        ev.consume();
    }

    private void onDragDropped(DragEvent ev) {
        boolean ok = false;
        if (ev.getDragboard().hasFiles()) {
            List<File> dropped = ev.getDragboard().getFiles().stream().filter(VolumesController::isSupportedBook).toList();
            if (!dropped.isEmpty()) {
                importFiles(dropped);
                ok = true;
            }
        }
        ev.setDropCompleted(ok);
        ev.consume();
    }

    /** Formats the app can open: EPUB (reflowable) and PDF (fixed pages). */
    private static boolean isSupportedBook(File f) {
        if (f == null || !f.isFile()) return false;
        String name = f.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".epub") || name.endsWith(".pdf");
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    @FXML
    private void onAddBooks() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select books");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Books (*.epub, *.pdf)", "*.epub", "*.pdf", "*.EPUB", "*.PDF"),
                new FileChooser.ExtensionFilter("EPUB (*.epub)", "*.epub", "*.EPUB"),
                new FileChooser.ExtensionFilter("PDF (*.pdf)", "*.pdf", "*.PDF"));
        Preferences prefs = dataManager.getPreferences();
        if (!prefs.getLastFolder().isEmpty()) {
            File folder = new File(prefs.getLastFolder());
            if (folder.isDirectory()) chooser.setInitialDirectory(folder);
        }
        List<File> chosen = chooser.showOpenMultipleDialog(navigator.getStage());
        if (chosen == null || chosen.isEmpty()) return;
        prefs.setLastFolder(chosen.get(0).getParentFile() == null ? "" : chosen.get(0).getParentFile().getAbsolutePath());
        importFiles(chosen);
    }

    /** Imports the files in natural order (Volume 2 before Volume 10), skipping duplicates. */
    private void importFiles(List<File> files) {
        List<File> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparing(File::getName, TextUtils::compareNatural));
        List<Book> added = new ArrayList<>();
        int order = dataManager.nextOrder(collection.getId());
        int duplicates = 0;
        for (File f : sorted) {
            if (!isSupportedBook(f)) continue;
            if (dataManager.hasBookWithPath(collection.getId(), f.getAbsolutePath())) {
                duplicates++;
                continue;
            }
            added.add(new Book(collection.getId(), TextUtils.titleFromFile(f), f.getAbsolutePath(), order++));
        }
        dataManager.addBooks(added);
        reload();
        if (!added.isEmpty()) {
            Book last = added.get(added.size() - 1);
            bookList.getSelectionModel().select(last);
            bookList.scrollTo(last);
        }
        if (duplicates > 0 && added.isEmpty()) {
            Dialogs.info(navigator.getStage(), "Nothing to add",
                    duplicates == 1 ? "That volume was already in the collection." : "Those volumes were already in the collection.");
        }
    }

    // ------------------------------------------------------------------
    // Actions on the selected volume
    // ------------------------------------------------------------------

    @FXML
    private void onMoveUp() {
        move(-1);
    }

    @FXML
    private void onMoveDown() {
        move(1);
    }

    private void move(int offset) {
        Book selected = bookList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        int index = dataManager.moveBook(selected, offset);
        if (index >= 0) {
            reload();
            bookList.getSelectionModel().select(index);
            bookList.scrollTo(Math.max(0, index - 2));
        }
        bookList.requestFocus();
    }

    @FXML
    private void onRename() {
        Book selected = bookList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Dialogs.askText(navigator.getStage(), "Rename volume", "Title:", selected.getTitle()).ifPresent(title -> {
            selected.setTitle(title);
            dataManager.save();
            bookList.refresh();
        });
    }

    @FXML
    private void onToggleRead() {
        Book selected = bookList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        boolean nowRead = !selected.isRead();
        selected.setRead(nowRead);
        if (nowRead) {
            selected.setReadingPercentage(100.0);
        }
        dataManager.save();
        reload();
        bookList.requestFocus();
    }

    private void resetProgress() {
        Book selected = bookList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        boolean ok = Dialogs.confirm(navigator.getStage(), "Reset progress",
                "\"" + selected.getTitle() + "\" will go back to the beginning and be marked as unread.", "Reset", false);
        if (ok) {
            selected.resetProgress();
            dataManager.save();
            reload();
        }
    }

    private void locateFile() {
        Book selected = bookList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Locate \"" + selected.getTitle() + "\"");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Books (*.epub, *.pdf)", "*.epub", "*.pdf", "*.EPUB", "*.PDF"));
        File current = selected.getFilePath() == null ? null : new File(selected.getFilePath()).getParentFile();
        if (current != null && current.isDirectory()) chooser.setInitialDirectory(current);
        File chosen = chooser.showOpenDialog(navigator.getStage());
        if (chosen != null && isSupportedBook(chosen)) {
            selected.setFilePath(chosen.getAbsolutePath());
            dataManager.save();
            reload();
        }
    }

    @FXML
    private void onRemove() {
        Book selected = bookList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        int index = bookList.getSelectionModel().getSelectedIndex();
        boolean ok = Dialogs.confirm(navigator.getStage(), "Remove \"" + selected.getTitle() + "\"",
                "It will be removed from the collection together with its reading progress. The file itself is not deleted.",
                "Remove", true);
        if (ok) {
            dataManager.deleteBook(selected);
            reload();
            if (!books.isEmpty()) {
                bookList.getSelectionModel().select(Math.min(index, books.size() - 1));
            }
            bookList.requestFocus();
        }
    }

    @FXML
    private void onOpenReader() {
        openSelectedInReader();
    }

    private void openSelectedInReader() {
        Book selected = bookList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (!fileExists(selected)) {
            Dialogs.error(navigator.getStage(), "File not found",
                    "The file could not be found:\n" + selected.getFilePath()
                            + "\n\nUse \"Locate file…\" in the context menu to point to its new location.");
            return;
        }
        navigator.showReader(selected);
    }

    // ------------------------------------------------------------------
    // Collection
    // ------------------------------------------------------------------

    @FXML
    private void onRenameCollection() {
        Dialogs.askText(navigator.getStage(), "Rename collection", "New name:", collection.getTitle())
                .ifPresent(name -> {
                    dataManager.renameCollection(collection, name);
                    collectionTitle.setText(name);
                });
    }

    @FXML
    private void onDeleteCollection() {
        int n = books.size();
        String detail = n == 0 ? "The collection is empty."
                : "Its " + TextUtils.plural(n, "volume", "volumes")
                + " and their reading progress will be removed from the library. The files themselves are not deleted.";
        boolean ok = Dialogs.confirm(navigator.getStage(), "Delete \"" + collection.getTitle() + "\"", detail,
                "Delete", true);
        if (ok) {
            dataManager.deleteCollection(collection);
            navigator.showCollections();
        }
    }

    @FXML
    private void onBack() {
        navigator.showCollections();
    }

    // ------------------------------------------------------------------
    // List cell
    // ------------------------------------------------------------------

    private final class BookCell extends ListCell<Book> {
        private final Label order = new Label();
        private final Label title = new Label();
        private final Label detail = new Label();
        private final ProgressBar bar = new ProgressBar(0);
        private final Label pill = new Label();
        private final Label format = new Label("PDF");
        private final HBox row;

        BookCell() {
            order.getStyleClass().add("row-order");
            format.getStyleClass().addAll("pill", "format");
            title.getStyleClass().add("row-title");
            detail.getStyleClass().add("row-detail");
            pill.getStyleClass().add("pill");
            bar.getStyleClass().add("thin-bar");
            bar.setPrefWidth(140);
            bar.setPrefHeight(5);
            HBox progress = new HBox(10, bar, detail);
            progress.setAlignment(Pos.CENTER_LEFT);
            VBox texts = new VBox(5, title, progress);
            HBox.setHgrow(texts, Priority.ALWAYS);
            row = new HBox(12, order, texts, format, pill);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(Book book, boolean empty) {
            super.updateItem(book, empty);
            if (empty || book == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            order.setText(String.format(Locale.ROOT, "%02d", book.getOrder()));
            title.setText(book.getTitle());
            boolean pdf = Book.FORMAT_PDF.equals(book.getFormat());
            format.setVisible(pdf);
            format.setManaged(pdf);
            double p = book.isRead() ? 100.0 : book.getReadingPercentage();
            bar.setProgress(p / 100.0);
            bar.getStyleClass().remove("complete");
            pill.getStyleClass().removeAll("read", "progress", "missing");
            boolean exists = fileExists(book);
            if (!exists) {
                pill.setText("File not found");
                pill.getStyleClass().add("missing");
                detail.setText(book.getFilePath());
            } else if (book.isRead()) {
                pill.setText("Read");
                pill.getStyleClass().add("read");
                bar.getStyleClass().add("complete");
                detail.setText("Completed");
            } else if (book.isStarted()) {
                pill.setText(String.format(Locale.ROOT, "%.0f %%", p));
                pill.getStyleClass().add("progress");
                detail.setText(String.format(Locale.ROOT, "%.1f %% read", p));
            } else {
                pill.setText("Not started");
                detail.setText("Not started");
            }
            setText(null);
            setGraphic(row);
        }
    }
}
