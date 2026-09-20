package com.devavaxp.reader;

import com.devavaxp.reader.model.Book;
import com.devavaxp.reader.model.Bookmark;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * The "Bookmarks" list of the readers' side panel, shared by the EPUB and the PDF reader:
 * shows where each bookmark is, the passage it starts with and its note; a click or Enter
 * jumps to it, N (or the context menu) edits the note, Delete removes it.
 * <p>
 * The reader that owns the pane says how a position is described and how to jump to it.
 */
final class BookmarksPane {

    interface Host {
        /** One line that tells where the bookmark is, e.g. "Chapter 3 · The Duel" or "Page 12". */
        String describe(Bookmark bookmark);

        void goTo(Bookmark bookmark);

        /** Called after the bookmarks of the book changed, so the library can be saved. */
        void changed();

        Window window();
    }

    private final Book book;
    private final ListView<Bookmark> list;
    private final Host host;
    private final Label placeholder = new Label("No bookmarks yet.\nPress B while reading to add one here.");

    BookmarksPane(Book book, ListView<Bookmark> list, Host host) {
        this.book = book;
        this.list = list;
        this.host = host;

        placeholder.getStyleClass().add("bookmark-placeholder");
        placeholder.setWrapText(true);
        list.setPlaceholder(placeholder);
        list.setCellFactory(view -> new BookmarkCell());
        list.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY && ev.getClickCount() == 1) goToSelected();
        });
        list.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case ENTER -> { goToSelected(); ev.consume(); }
                case DELETE, BACK_SPACE -> { removeSelected(); ev.consume(); }
                case N, F2 -> { editNoteOfSelected(); ev.consume(); }
                default -> { }
            }
        });
        MenuItem note = new MenuItem("Edit note…");
        note.setOnAction(e -> editNoteOfSelected());
        MenuItem remove = new MenuItem("Remove bookmark");
        remove.setOnAction(e -> removeSelected());
        list.setContextMenu(new ContextMenu(note, new SeparatorMenuItem(), remove));
        refresh();
    }

    /** Reloads the list from the book (after adding or removing a bookmark elsewhere). */
    void refresh() {
        Bookmark selected = list.getSelectionModel().getSelectedItem();
        list.getItems().setAll(book.getBookmarks());
        if (selected != null && list.getItems().contains(selected)) {
            list.getSelectionModel().select(selected);
        }
    }

    void select(Bookmark bookmark) {
        if (bookmark != null && list.getItems().contains(bookmark)) {
            list.getSelectionModel().select(bookmark);
            list.scrollTo(bookmark);
        }
    }

    private void goToSelected() {
        Bookmark selected = list.getSelectionModel().getSelectedItem();
        if (selected != null) host.goTo(selected);
    }

    void editNoteOfSelected() {
        Bookmark selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Dialogs.editText(host.window(), "Bookmark note", "Note (leave empty to remove it):", selected.getNote())
                .ifPresent(text -> {
                    selected.setNote(text);
                    list.refresh();
                    host.changed();
                });
    }

    void removeSelected() {
        Bookmark selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        int index = list.getSelectionModel().getSelectedIndex();
        book.removeBookmark(selected);
        refresh();
        if (!list.getItems().isEmpty()) {
            list.getSelectionModel().select(Math.min(index, list.getItems().size() - 1));
        }
        host.changed();
    }

    /** Three lines at most: where, the first words of the passage, and the note. */
    private final class BookmarkCell extends ListCell<Bookmark> {
        private final Label where = new Label();
        private final Label excerpt = new Label();
        private final Label note = new Label();
        private final VBox box = new VBox(2, where, excerpt, note);

        BookmarkCell() {
            where.getStyleClass().add("bookmark-where");
            excerpt.getStyleClass().add("bookmark-excerpt");
            note.getStyleClass().add("bookmark-note");
            for (Label l : new Label[]{where, excerpt, note}) {
                l.setWrapText(true);
                l.maxWidthProperty().bind(list.widthProperty().subtract(36));
            }
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(Bookmark item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            where.setText(host.describe(item));
            excerpt.setText(item.getExcerpt());
            excerpt.setVisible(!item.getExcerpt().isEmpty());
            excerpt.setManaged(excerpt.isVisible());
            note.setText(item.getNote());
            note.setVisible(item.hasNote());
            note.setManaged(note.isVisible());
            setGraphic(box);
        }
    }
}
