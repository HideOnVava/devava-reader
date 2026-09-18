package com.devavaxp.reader;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;

import java.util.function.Consumer;

/**
 * Small reusable controls built in code.
 */
final class UiControls {

    private UiControls() {
    }

    /**
     * "Full screen" icon (four corners) drawn as a path, because the text symbol for it is
     * missing from the fonts of many Linux desktops. Colours come from the .icon-shape style.
     */
    static Node fullScreenIcon() {
        SVGPath path = new SVGPath();
        path.setContent("M0 4.5 V0 H4.5 M8.5 0 H13 V4.5 M13 8.5 V13 H8.5 M4.5 13 H0 V8.5");
        path.setStrokeLineCap(StrokeLineCap.ROUND);
        path.getStyleClass().add("icon-shape");
        return path;
    }

    /**
     * A row of mutually exclusive buttons (a "segmented control"). {@code options} are
     * {value, label} pairs; {@code current} selects the initial value. One option is always
     * active: deselecting the active one is reverted.
     */
    static HBox segmented(String current, Consumer<String> onChange, String[][] options) {
        ToggleGroup group = new ToggleGroup();
        HBox row = new HBox(0);
        row.getStyleClass().add("segmented");
        row.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < options.length; i++) {
            String value = options[i][0];
            ToggleButton button = new ToggleButton(options[i][1]);
            button.setToggleGroup(group);
            button.setUserData(value);
            button.setSelected(value.equalsIgnoreCase(current));
            button.setMaxWidth(Double.MAX_VALUE);
            button.setFocusTraversable(false);
            HBox.setHgrow(button, Priority.ALWAYS);
            if (i == 0) button.getStyleClass().add("first");
            if (i == options.length - 1) button.getStyleClass().add("last");
            row.getChildren().add(button);
        }
        group.selectedToggleProperty().addListener((obs, previous, selected) -> {
            if (selected == null) {
                if (previous != null) group.selectToggle(previous);
                return;
            }
            onChange.accept(String.valueOf(selected.getUserData()));
        });
        return row;
    }

    /** Selects the option with the given value in a row built by {@link #segmented}. */
    static void select(HBox segmentedRow, String value) {
        for (var node : segmentedRow.getChildren()) {
            if (node instanceof ToggleButton button && value.equalsIgnoreCase(String.valueOf(button.getUserData()))) {
                button.setSelected(true);
                return;
            }
        }
    }

    /** Value of the selected option in a row built by {@link #segmented}, or null. */
    static String selected(HBox segmentedRow) {
        for (var node : segmentedRow.getChildren()) {
            if (node instanceof Toggle toggle && toggle.isSelected()) {
                return String.valueOf(toggle.getUserData());
            }
        }
        return null;
    }
}
