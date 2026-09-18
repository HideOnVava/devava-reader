package com.devavaxp.reader;

import com.devavaxp.reader.data.AppDirectories;
import com.devavaxp.reader.data.DataManager;
import com.devavaxp.reader.data.TextUtils;
import com.devavaxp.reader.model.Book;
import com.devavaxp.reader.model.BookCollection;
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
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Main screen: the list of collections plus quick access to the last book being read.
 * <p>
 * The list can be searched by name and filtered by the format of the volumes a
 * collection holds. Pinned collections are always listed first, and collections can be
 * moved up and down; moving is only offered while the full, unfiltered list is visible,
 * so that a position always means the real position.
 */
public class CollectionsController implements Navigator.Screen {

    /** Values of the format filter. */
    private static final String FILTER_ALL = "all";
    private static final String FILTER_EPUB = Book.FORMAT_EPUB;
    private static final String FILTER_PDF = Book.FORMAT_PDF;

    @FXML private Label summaryLabel;
    @FXML private HBox filterBar;
    @FXML private TextField searchField;
    @FXML private ListView<BookCollection> collectionList;
    @FXML private TextField newCollectionField;
    @FXML private Button openButton;
    @FXML private Button moveUpButton;
    @FXML private Button moveDownButton;
    @FXML private HBox continueCard;
    @FXML private Label continueTitleLabel;
    @FXML private Label continueProgressLabel;
    @FXML private ProgressBar continueBar;

    private Navigator navigator;
    private DataManager dataManager;
    private HBox formatFilter;
    private String activeFilter = FILTER_ALL;
    private List<BookCollection> allCollections = List.of();
    private ObservableList<BookCollection> visibleCollections = FXCollections.observableArrayList();
    private Book bookToContinue;
    private MenuItem pinMenuItem;

    // ------------------------------------------------------------------
    // Initialization
    // ------------------------------------------------------------------

    public void init(Navigator navigator) {
        this.navigator = navigator;
        this.dataManager = navigator.getDataManager();

        collectionList.setCellFactory(list -> new CollectionCell());
        collectionList.setItems(visibleCollections);
        collectionList.getSelectionModel().selectedItemProperty()
                .addListener((obs, previous, selected) -> updateButtons());
        collectionList.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2) openSelected();
        });
        collectionList.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case ENTER -> { openSelected(); ev.consume(); }
                case DELETE -> { deleteSelected(); ev.consume(); }
                case F2 -> { renameSelected(); ev.consume(); }
                case UP -> { if (ev.isShortcutDown()) { onMoveUp(); ev.consume(); } }
                case DOWN -> { if (ev.isShortcutDown()) { onMoveDown(); ev.consume(); } }
                default -> { }
            }
        });
        collectionList.setContextMenu(createContextMenu());
        String mod = AppDirectories.shortcutKey();
        moveUpButton.setTooltip(new Tooltip("Move up (" + mod + "+↑). Available when no search or filter is active"));
        moveDownButton.setTooltip(new Tooltip("Move down (" + mod + "+↓). Available when no search or filter is active"));

        // Search box and format filter
        searchField.textProperty().addListener((obs, previous, text) -> applyFilters());
        searchField.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                searchField.clear();
                ev.consume();
            } else if (ev.getCode() == KeyCode.DOWN) {
                collectionList.requestFocus();
                if (collectionList.getSelectionModel().isEmpty()) collectionList.getSelectionModel().selectFirst();
                ev.consume();
            }
        });
        formatFilter = UiControls.segmented(FILTER_ALL, this::changeFilter, new String[][]{
                {FILTER_ALL, "All"}, {FILTER_EPUB, "EPUB"}, {FILTER_PDF, "PDF"}});
        formatFilter.setMinWidth(220);
        filterBar.getChildren().add(formatFilter);

        reload();
        if (allCollections.isEmpty()) {
            newCollectionField.requestFocus();
        } else {
            collectionList.getSelectionModel().selectFirst();
            collectionList.requestFocus();
        }
    }

    private ContextMenu createContextMenu() {
        MenuItem open = new MenuItem("Open");
        open.setOnAction(e -> openSelected());
        pinMenuItem = new MenuItem("Pin");
        pinMenuItem.setOnAction(e -> togglePinSelected());
        MenuItem rename = new MenuItem("Rename…");
        rename.setOnAction(e -> renameSelected());
        MenuItem delete = new MenuItem("Delete collection…");
        delete.getStyleClass().add("danger");
        delete.setOnAction(e -> deleteSelected());
        ContextMenu menu = new ContextMenu(open, pinMenuItem, rename, new SeparatorMenuItem(), delete);
        menu.setOnShowing(e -> {
            BookCollection selected = collectionList.getSelectionModel().getSelectedItem();
            pinMenuItem.setText(selected != null && selected.isPinned() ? "Unpin" : "Pin");
            pinMenuItem.setDisable(selected == null);
        });
        return menu;
    }

    // ------------------------------------------------------------------
    // Data -> screen
    // ------------------------------------------------------------------

    /** Re-reads the library and refreshes everything on screen. */
    private void reload() {
        allCollections = dataManager.getCollections();
        applyFilters();

        Optional<Book> last = dataManager.lastReadBook();
        bookToContinue = last.orElse(null);
        boolean available = bookToContinue != null;
        continueCard.setVisible(available);
        continueCard.setManaged(available);
        if (available) {
            String collection = dataManager.findCollection(bookToContinue.getCollectionId())
                    .map(BookCollection::getTitle).orElse("");
            continueTitleLabel.setText(collection.isEmpty() ? bookToContinue.getTitle()
                    : collection + "  ·  " + bookToContinue.getTitle());
            double p = bookToContinue.getReadingPercentage();
            continueBar.setProgress(p / 100.0);
            continueProgressLabel.setText(String.format(Locale.ROOT, "%.0f %%", p));
        }
    }

    /** Applies the search text and the format filter on top of the display order. */
    private void applyFilters() {
        BookCollection selected = collectionList.getSelectionModel().getSelectedItem();
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);

        List<BookCollection> matching = allCollections.stream()
                .filter(c -> query.isEmpty() || c.getTitle().toLowerCase(Locale.ROOT).contains(query))
                .filter(this::matchesFormatFilter)
                .toList();
        visibleCollections.setAll(matching);
        if (selected != null && matching.contains(selected)) {
            collectionList.getSelectionModel().select(selected);
        }

        boolean filtering = isFiltering();
        collectionList.setPlaceholder(new Label(filtering
                ? "No collections match."
                : "No collections yet. Create one to get started."));

        int total = allCollections.size();
        int books = dataManager.getBooks().size();
        if (total == 0) {
            summaryLabel.setText("Your collections");
        } else if (filtering) {
            summaryLabel.setText("Showing " + matching.size() + " of " + TextUtils.plural(total, "collection", "collections"));
        } else {
            summaryLabel.setText(TextUtils.plural(total, "collection", "collections") + " · "
                    + TextUtils.plural(books, "volume", "volumes"));
        }
        updateButtons();
    }

    private boolean matchesFormatFilter(BookCollection collection) {
        return switch (activeFilter) {
            case FILTER_EPUB -> dataManager.hasOnlyFormat(collection, Book.FORMAT_EPUB);
            case FILTER_PDF -> dataManager.hasOnlyFormat(collection, Book.FORMAT_PDF);
            default -> true;
        };
    }

    private boolean isFiltering() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        return !query.isEmpty() || !FILTER_ALL.equals(activeFilter);
    }

    private void changeFilter(String value) {
        activeFilter = value == null ? FILTER_ALL : value;
        applyFilters();
    }

    private void updateButtons() {
        BookCollection selected = collectionList.getSelectionModel().getSelectedItem();
        boolean any = selected != null;
        openButton.setDisable(!any);
        // Reordering only makes sense on the complete list, where positions are real.
        boolean canReorder = any && !isFiltering();
        moveUpButton.setDisable(!canReorder || !dataManager.canMoveCollection(selected, -1));
        moveDownButton.setDisable(!canReorder || !dataManager.canMoveCollection(selected, 1));
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    @FXML
    private void onCreateCollection() {
        String name = newCollectionField.getText() == null ? "" : newCollectionField.getText().trim();
        if (name.isEmpty()) {
            newCollectionField.requestFocus();
            return;
        }
        BookCollection created = new BookCollection(name);
        dataManager.addCollection(created);
        newCollectionField.clear();
        // A new collection must be visible: clear any search or filter that would hide it.
        searchField.clear();
        UiControls.select(formatFilter, FILTER_ALL);
        reload();
        collectionList.getSelectionModel().select(created);
        collectionList.scrollTo(created);
    }

    @FXML
    private void onOpenCollection() {
        openSelected();
    }

    @FXML
    private void onContinueReading() {
        if (bookToContinue != null) {
            navigator.showReader(bookToContinue);
        }
    }

    @FXML
    private void onMoveUp() {
        moveSelected(-1);
    }

    @FXML
    private void onMoveDown() {
        moveSelected(1);
    }

    private void moveSelected(int offset) {
        BookCollection selected = collectionList.getSelectionModel().getSelectedItem();
        if (selected == null || isFiltering()) return;
        int index = dataManager.moveCollection(selected, offset);
        if (index >= 0) {
            reload();
            collectionList.getSelectionModel().select(index);
            collectionList.scrollTo(Math.max(0, index - 2));
        }
        collectionList.requestFocus();
    }

    private void togglePinSelected() {
        BookCollection selected = collectionList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        dataManager.setCollectionPinned(selected, !selected.isPinned());
        reload();
        collectionList.getSelectionModel().select(selected);
        collectionList.scrollTo(selected);
        collectionList.requestFocus();
    }

    private void openSelected() {
        BookCollection selected = collectionList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            navigator.showVolumes(selected);
        }
    }

    private void renameSelected() {
        BookCollection selected = collectionList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Dialogs.askText(navigator.getStage(), "Rename collection", "New name:", selected.getTitle())
                .ifPresent(name -> {
                    dataManager.renameCollection(selected, name);
                    reload();
                    collectionList.getSelectionModel().select(selected);
                });
    }

    private void deleteSelected() {
        BookCollection selected = collectionList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        int n = dataManager.countBooks(selected);
        String detail = n == 0 ? "The collection is empty."
                : "Its " + TextUtils.plural(n, "volume", "volumes")
                + " and their reading progress will be removed from the library. The files themselves are not deleted.";
        boolean ok = Dialogs.confirm(navigator.getStage(), "Delete \"" + selected.getTitle() + "\"",
                detail, "Delete", true);
        if (ok) {
            dataManager.deleteCollection(selected);
            reload();
        }
    }

    // ------------------------------------------------------------------
    // List cell
    // ------------------------------------------------------------------

    private final class CollectionCell extends ListCell<BookCollection> {
        private final Label avatar = new Label();
        private final Label title = new Label();
        private final Label detail = new Label();
        private final Label pin = new Label("★");
        private final Label pill = new Label();
        private final HBox row;

        CollectionCell() {
            avatar.getStyleClass().add("avatar");
            title.getStyleClass().add("row-title");
            detail.getStyleClass().add("row-detail");
            pin.getStyleClass().add("pin-mark");
            pill.getStyleClass().add("pill");
            VBox texts = new VBox(2, title, detail);
            HBox.setHgrow(texts, Priority.ALWAYS);
            row = new HBox(12, avatar, texts, pin, pill);
            row.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(BookCollection item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            int total = dataManager.countBooks(item);
            int read = dataManager.countRead(item);
            String t = item.getTitle() == null ? "" : item.getTitle().trim();
            avatar.setText(t.isEmpty() ? "?" : t.substring(0, 1).toUpperCase(Locale.ROOT));
            title.setText(item.getTitle());
            detail.setText(total == 0 ? "No volumes"
                    : TextUtils.plural(total, "volume", "volumes") + (read > 0 ? " · " + read + " read" : ""));
            pin.setVisible(item.isPinned());
            pin.setManaged(item.isPinned());
            pill.getStyleClass().removeAll("read", "progress");
            if (total > 0 && read == total) {
                pill.setText("Complete");
                pill.getStyleClass().add("read");
                pill.setVisible(true);
            } else if (read > 0) {
                pill.setText(read + " / " + total);
                pill.getStyleClass().add("progress");
                pill.setVisible(true);
            } else {
                pill.setVisible(false);
            }
            setText(null);
            setGraphic(row);
        }
    }
}
