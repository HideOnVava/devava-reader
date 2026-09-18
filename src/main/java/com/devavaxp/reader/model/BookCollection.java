package com.devavaxp.reader.model;

import com.google.gson.annotations.SerializedName;

import java.util.UUID;

/**
 * A collection of volumes in the library (for example, a light novel series).
 * <p>
 * Collections have a custom position ({@code order}) and can be pinned; pinned
 * collections are always listed before the rest. Both fields are optional in the
 * JSON: files written by earlier versions simply keep their original order and
 * have nothing pinned.
 * <p>
 * JSON field names carry {@code alternate} values so that library files written by
 * earlier versions of the application (Spanish field names) can still be loaded.
 */
public class BookCollection {

    @SerializedName("id")
    private String id;

    @SerializedName(value = "title", alternate = {"titulo"})
    private String title;

    /** Custom position (1-based). 0 means "not assigned yet" and is normalized on load. */
    @SerializedName("order")
    private int order;

    @SerializedName("pinned")
    private boolean pinned;

    public BookCollection(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.order = 0;
        this.pinned = false;
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

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    @Override
    public String toString() {
        return title;
    }
}
