package com.devavaxp.reader;

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
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.Optional;

/**
 * Main screen: the list of collections plus quick access to the last book being read.
 */
public class CollectionsController implements Navigator.Screen {

    @FXML private Label summaryLabel;
    @FXML private ListView<BookCollection> collectionList;
    @FXML private TextField newCollectionField;
    @FXML private Button openButton;
    @FXML private HBox continueCard;
    @FXML private Label continueTitleLabel;
    @FXML private Label continueProgressLabel;
    @FXML private ProgressBar continueBar;

    private Navigator navigator;
    private DataManager dataManager;
    private ObservableList<BookCollection> collections;
    private Book bookToContinue;

    // ------------------------------------------------------------------
    // Initialization
    // ------------------------------------------------------------------

    public void init(Navigator navigator) {
        this.navigator = navigator;
        this.dataManager = navigator.getDataManager();

        collectionList.setCellFactory(list -> new CollectionCell());
        collectionList.setPlaceholder(new Label("No collections yet. Create one to get started."));
        collectionList.getSelectionModel().selectedItemProperty()
                .addListener((obs, previous, selected) -> openButton.setDisable(selected == null));
        collectionList.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 2) openSelected();
        });
        collectionList.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case ENTER -> { openSelected(); ev.consume(); }
                case DELETE -> { deleteSelected(); ev.consume(); }
                case F2 -> { renameSelected(); ev.consume(); }
                default -> { }
            }
        });
        collectionList.setContextMenu(createContextMenu());

        reload();
        if (collections.isEmpty()) {
            newCollectionField.requestFocus();
        } else {
            collectionList.getSelectionModel().selectFirst();
            collectionList.requestFocus();
        }
    }

    private ContextMenu createContextMenu() {
        MenuItem open = new MenuItem("Open");
        open.setOnAction(e -> openSelected());
        MenuItem rename = new MenuItem("Rename…");
        rename.setOnAction(e -> renameSelected());
        MenuItem delete = new MenuItem("Delete collection…");
        delete.getStyleClass().add("danger");
        delete.setOnAction(e -> deleteSelected());
        return new ContextMenu(open, rename, new SeparatorMenuItem(), delete);
    }

    private void reload() {
        BookCollection selected = collectionList.getSelectionModel().getSelectedItem();
        collections = FXCollections.observableArrayList(dataManager.getCollections());
        collectionList.setItems(collections);
        if (selected != null) collectionList.getSelectionModel().select(selected);

        int total = collections.size();
        int books = dataManager.getBooks().size();
        summaryLabel.setText(total == 0 ? "Your collections"
                : TextUtils.plural(total, "collection", "collections") + " · " + TextUtils.plural(books, "volume", "volumes"));

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
                    collectionList.refresh();
                    reload();
                });
    }

    private void deleteSelected() {
        BookCollection selected = collectionList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        int n = dataManager.countBooks(selected);
        String detail = n == 0 ? "The collection is empty."
                : "Its " + TextUtils.plural(n, "volume", "volumes")
                + " and their reading progress will be removed from the library. The .epub files are not deleted.";
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
        private final Label pill = new Label();
        private final HBox row;

        CollectionCell() {
            avatar.getStyleClass().add("avatar");
            title.getStyleClass().add("row-title");
            detail.getStyleClass().add("row-detail");
            pill.getStyleClass().add("pill");
            VBox texts = new VBox(2, title, detail);
            HBox.setHgrow(texts, Priority.ALWAYS);
            row = new HBox(12, avatar, texts, pill);
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
