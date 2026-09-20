package com.devavaxp.reader;

import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The side panel of both readers: a row of tabs (Contents | Bookmarks | Search…) above
 * one view per tab, only one of which is shown. A tab can be marked unavailable (a book
 * without a table of contents has no Contents), in which case the panel opens on the next
 * one and the switch cycles over the available tabs only.
 */
final class ReaderSidePanel {

    enum Tab {
        CONTENTS("contents", "Contents"),
        BOOKMARKS("bookmarks", "Bookmarks"),
        SEARCH("search", "Search");

        final String value;
        final String label;

        Tab(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    /** A tab: its view and the node that takes the focus when the tab is shown. */
    record TabView(Tab tab, Node view, Node focusTarget) {
    }

    private final VBox panel;
    private final HBox tabs;
    private final List<TabView> views;
    private final Runnable onHide;
    private final List<Tab> unavailable = new ArrayList<>();
    private Tab tab;

    /**
     * @param onHide gives the focus back to the reading area when the panel closes
     */
    ReaderSidePanel(VBox panel, HBox tabsContainer, Runnable onHide, TabView... tabViews) {
        this.panel = panel;
        this.views = List.of(tabViews);
        this.onHide = onHide;
        this.tab = views.get(0).tab();
        String[][] options = new String[views.size()][];
        for (int i = 0; i < views.size(); i++) {
            options[i] = new String[]{views.get(i).tab().value, views.get(i).tab().label};
        }
        this.tabs = UiControls.segmented(tab.value, value -> {
            for (TabView v : views) {
                if (v.tab().value.equals(value)) switchTo(v.tab());
            }
        }, options);
        this.tabs.setMaxWidth(Double.MAX_VALUE);
        tabsContainer.getChildren().setAll(tabs);
        HBox.setHgrow(tabs, Priority.ALWAYS);
        applyTab();
    }

    /** Marks a tab as (un)available; an unavailable current tab gives way to the next one. */
    void setAvailable(Tab which, boolean available) {
        unavailable.remove(which);
        if (!available) unavailable.add(which);
        UiControls.setEnabled(tabs, which.value, available);
        if (!available && tab == which) switchTo(nextAvailable(which));
    }

    boolean isVisible() {
        return panel.isVisible();
    }

    Tab tab() {
        return tab;
    }

    /** Shows the panel on the given tab, or on the next available one. */
    void show(Tab wanted) {
        panel.setVisible(true);
        panel.setManaged(true);
        switchTo(unavailable.contains(wanted) ? nextAvailable(wanted) : wanted);
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
        UiControls.select(tabs, wanted.value);
        applyTab();
        if (isVisible()) focusCurrent();
    }

    /** The next available tab, cycling (the Tab key inside the panel). */
    void switchToOther() {
        switchTo(nextAvailable(tab));
    }

    private Tab nextAvailable(Tab from) {
        int start = indexOf(from);
        for (int step = 1; step <= views.size(); step++) {
            Tab candidate = views.get((start + step) % views.size()).tab();
            if (!unavailable.contains(candidate)) return candidate;
        }
        return from;
    }

    private int indexOf(Tab which) {
        for (int i = 0; i < views.size(); i++) {
            if (views.get(i).tab() == which) return i;
        }
        return 0;
    }

    /** Focuses the current tab's target; lists get a selected row so that keys act on it. */
    private void focusCurrent() {
        Node target = views.get(indexOf(tab)).focusTarget();
        if (target instanceof ListView<?> list) {
            if (list.getSelectionModel().isEmpty() && !list.getItems().isEmpty()) {
                list.getSelectionModel().selectFirst();
            }
        } else if (target instanceof TextInputControl field) {
            field.selectAll();
        }
        target.requestFocus();
    }

    private void applyTab() {
        for (TabView v : views) {
            boolean shown = v.tab() == tab;
            v.view().setVisible(shown);
            v.view().setManaged(shown);
        }
    }
}
