package com.devavaxp.reader.bridge;

import javafx.application.Platform;

/**
 * Object exposed to the reader's JavaScript as {@code window.readerBridge}.
 * <p>
 * It must be a public class with public methods (the WebView invokes them reflectively)
 * and the controller must keep a strong reference to the instance, because the WebView
 * only holds it weakly. Every call is forwarded to the JavaFX thread on a later pulse so
 * that JavaScript is never executed re-entrantly.
 */
public final class JsBridge {

    public interface Listener {
        void onEvent(String event, String data);

        void onLink(String absoluteHref, String originalHref);
    }

    private final Listener listener;

    public JsBridge(Listener listener) {
        this.listener = listener;
    }

    public void onEvent(String event, String data) {
        Platform.runLater(() -> listener.onEvent(event, data));
    }

    public void onLink(String absoluteHref, String originalHref) {
        Platform.runLater(() -> listener.onLink(absoluteHref, originalHref));
    }

    public void log(String message) {
        System.out.println("[reader.js] " + message);
    }
}
