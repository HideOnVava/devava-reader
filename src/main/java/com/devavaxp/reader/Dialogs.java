package com.devavaxp.reader;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Dialog boxes styled like the rest of the application.
 */
public final class Dialogs {

    private Dialogs() {
    }

    private static void style(Dialog<?> dialog, Window owner) {
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        dialog.getDialogPane().getStyleClass().add("dialog");
    }

    public static void info(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        style(alert, owner);
        alert.showAndWait();
    }

    public static void error(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        style(alert, owner);
        alert.showAndWait();
    }

    /** Yes/no question. A destructive confirmation button is shown in red. */
    public static boolean confirm(Window owner, String title, String message, String confirmText, boolean destructive) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        ButtonType confirm = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(confirm, cancel);
        style(alert, owner);
        if (destructive) {
            alert.getDialogPane().lookupButton(confirm).getStyleClass().add("danger");
        }
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == confirm;
    }

    /** Asks for a non-empty text. Returns empty when cancelled or left blank. */
    public static Optional<String> askText(Window owner, String title, String label, String initialValue) {
        return editText(owner, title, label, initialValue).filter(s -> !s.isEmpty());
    }

    /** Asks for a text that may be left blank (to clear a value). Returns empty only when cancelled. */
    public static Optional<String> editText(Window owner, String title, String label, String initialValue) {
        TextInputDialog dialog = new TextInputDialog(initialValue == null ? "" : initialValue);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setContentText(label);
        style(dialog, owner);
        dialog.getEditor().setPrefColumnCount(28);
        return dialog.showAndWait().map(String::trim);
    }
}
