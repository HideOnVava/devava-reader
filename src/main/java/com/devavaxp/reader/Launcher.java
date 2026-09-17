package com.devavaxp.reader;

import javafx.application.Application;

/**
 * Alternative launcher, useful when running from a plain JAR or from IDEs that do not
 * detect the {@link Application} subclass directly.
 */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(ReaderApp.class, args);
    }
}
