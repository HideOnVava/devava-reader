package com.devavaxp.reader.model;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;

/**
 * User preferences: reader appearance and window state.
 * They live inside the library file; any missing field takes its default value.
 * <p>
 * JSON field names carry {@code alternate} values so that library files written by
 * earlier versions of the application (Spanish field names and values) still load.
 */
public class Preferences {

    public static final int FONT_MIN = 12;
    public static final int FONT_MAX = 40;

    public static final String THEME_LIGHT = "light";
    public static final String THEME_SEPIA = "sepia";
    public static final String THEME_DARK = "dark";

    public static final String TYPEFACE_SERIF = "serif";
    public static final String TYPEFACE_SANS = "sans";

    @SerializedName(value = "fontSize", alternate = {"tamanoFuente"})
    private int fontSize = 20;

    @SerializedName(value = "theme", alternate = {"tema"})
    private String theme = THEME_LIGHT;

    @SerializedName(value = "typeface", alternate = {"tipografia"})
    private String typeface = TYPEFACE_SERIF;

    @SerializedName(value = "columns", alternate = {"columnas"})
    private int columns = 2;

    @SerializedName(value = "windowWidth", alternate = {"ventanaAncho"})
    private double windowWidth = 1000;

    @SerializedName(value = "windowHeight", alternate = {"ventanaAlto"})
    private double windowHeight = 680;

    @SerializedName(value = "windowMaximized", alternate = {"ventanaMaximizada"})
    private boolean windowMaximized = false;

    /** Last folder used when importing EPUB files. */
    @SerializedName(value = "lastFolder", alternate = {"ultimaCarpeta"})
    private String lastFolder = "";

    public int getFontSize() {
        return Math.max(FONT_MIN, Math.min(FONT_MAX, fontSize));
    }

    public void setFontSize(int fontSize) {
        this.fontSize = Math.max(FONT_MIN, Math.min(FONT_MAX, fontSize));
    }

    /** Theme name, normalized; legacy Spanish values are mapped to their English names. */
    public String getTheme() {
        String t = theme == null ? "" : theme.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case THEME_SEPIA -> THEME_SEPIA;
            case THEME_DARK, "oscuro" -> THEME_DARK;
            default -> THEME_LIGHT;
        };
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getTypeface() {
        return TYPEFACE_SANS.equalsIgnoreCase(typeface) ? TYPEFACE_SANS : TYPEFACE_SERIF;
    }

    public void setTypeface(String typeface) {
        this.typeface = typeface;
    }

    public int getColumns() {
        return columns == 1 ? 1 : 2;
    }

    public void setColumns(int columns) {
        this.columns = columns == 1 ? 1 : 2;
    }

    public double getWindowWidth() {
        return windowWidth > 0 ? windowWidth : 1000;
    }

    public void setWindowWidth(double windowWidth) {
        this.windowWidth = windowWidth;
    }

    public double getWindowHeight() {
        return windowHeight > 0 ? windowHeight : 680;
    }

    public void setWindowHeight(double windowHeight) {
        this.windowHeight = windowHeight;
    }

    public boolean isWindowMaximized() {
        return windowMaximized;
    }

    public void setWindowMaximized(boolean windowMaximized) {
        this.windowMaximized = windowMaximized;
    }

    public String getLastFolder() {
        return lastFolder == null ? "" : lastFolder;
    }

    public void setLastFolder(String lastFolder) {
        this.lastFolder = lastFolder;
    }
}
