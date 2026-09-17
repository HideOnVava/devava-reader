package com.devavaxp.reader.model;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

/**
 * A collection of volumes in the library (for example, a light novel series).
 * <p>
 * JSON field names carry {@code alternate} values so that library files written by
 * earlier versions of the application (Spanish field names) can still be loaded.
 */
public class BookCollection {

    @SerializedName("id")
    private String id;

    @SerializedName(value = "title", alternate = {"titulo"})
    private String title;

    public BookCollection(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return title;
    }
}
