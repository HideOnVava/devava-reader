package com.devavaxp.reader;

import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The side panel of both readers: a Contents | Bookmarks switch above two lists, only one
 * of which is shown. The Contents tab is disabled for books without a table of contents,
 * in which case the panel opens on the bookmarks.
 */
final class ReaderSidePanel {

    enum Tab { CONTENTS, BOOKMARKS }

    private static final String CONTENTS = "contents";
    private static final String BOOKMARKS = "bookmarks";

    private final VBox panel;
    private final HBox tabs;
    private final ListView<?> contentsList;
    private final ListView<?> bookmarkList;
    private final Runnable onHide;
    private Tab tab = Tab.CONTENTS;
    private boolean contentsAvailable = true;

    /**
     * @param onHide gives the focus back to the reading area when the panel closes
     */
    ReaderSidePanel(VBox panel, HBox tabsContainer, ListView<?> contentsList, ListView<?> bookmarkList, Runnable onHide) {
        this.panel = panel;
        this.contentsList = contentsList;
        this.bookmarkList = bookmarkList;
        this.onHide = onHide;
        this.tabs = UiControls.segmented(CONTENTS, value -> switchTo(BOOKMARKS.equals(value) ? Tab.BOOKMARKS : Tab.CONTENTS),
                new String[][]{{CONTENTS, "Contents"}, {BOOKMARKS, "Bookmarks"}});
        this.tabs.setMaxWidth(Double.MAX_VALUE);
        tabsContainer.getChildren().setAll(tabs);
        HBox.setHgrow(tabs, Priority.ALWAYS);
        applyTab();
    }

    /** Books without a table of contents keep the Bookmarks tab only. */
    void setContentsAvailable(boolean available) {
        contentsAvailable = available;
        UiControls.setEnabled(tabs, CONTENTS, available);
        if (!available && tab == Tab.CONTENTS) switchTo(Tab.BOOKMARKS);
    }

    boolean isVisible() {
        return panel.isVisible();
    }

    Tab tab() {
        return tab;
    }

    /** Shows the panel on the given tab (or on Bookmarks when there are no contents). */
    void show(Tab wanted) {
        Tab effective = wanted == Tab.CONTENTS && !contentsAvailable ? Tab.BOOKMARKS : wanted;
        panel.setVisible(true);
        panel.setManaged(true);
        switchTo(effective);
    }

    void hide() {
        panel.setVisible(false);
        panel.setManaged(false);
        onHide.run();
    }

    /** Hides the panel when it is shown; otherwise shows it on the preferred tab. */
    void toggle(Tab preferred) {
        if (isVisible()) hide(); else show(preferred);
    }

    void switchTo(Tab wanted) {
        tab = wanted;
        UiControls.select(tabs, wanted == Tab.BOOKMARKS ? BOOKMARKS : CONTENTS);
        applyTab();
        if (isVisible()) focusList();
    }

    /** The other tab, when it is available (Tab key inside the panel). */
    void switchToOther() {
        if (tab == Tab.BOOKMARKS && contentsAvailable) switchTo(Tab.CONTENTS);
        else if (tab == Tab.CONTENTS) switchTo(Tab.BOOKMARKS);
    }

    /** Focuses the visible list and makes sure a row is selected, so that keys act on it. */
    private void focusList() {
        ListView<?> list = currentList();
        if (list.getSelectionModel().isEmpty() && !list.getItems().isEmpty()) {
            list.getSelectionModel().selectFirst();
        }
        list.requestFocus();
    }

    ListView<?> currentList() {
        return tab == Tab.BOOKMARKS ? bookmarkList : contentsList;
    }

    private void applyTab() {
        boolean bookmarks = tab == Tab.BOOKMARKS;
        contentsList.setVisible(!bookmarks);
        contentsList.setManaged(!bookmarks);
        bookmarkList.setVisible(bookmarks);
        bookmarkList.setManaged(bookmarks);
    }
}
