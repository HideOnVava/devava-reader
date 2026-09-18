package com.devavaxp.reader.data;

import com.devavaxp.reader.model.Book;
import com.devavaxp.reader.model.BookCollection;
import com.devavaxp.reader.model.Preferences;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Persists the whole library (collections, books and preferences) in a single JSON file.
 * <p>
 * By default the file lives in {@code <Documents>/Devava Reader/library.json}, where
 * {@code <Documents>} is the user's real Documents folder (see {@link AppDirectories} for
 * each operating system). The location can be overridden with the system property
 * {@code -Dreader.library=path}.
 * <p>
 * Writes are atomic (a temporary file is written and then renamed), so an unexpected
 * shutdown never leaves the library half-written. The first time the application runs,
 * a library file created by an earlier version (Spanish field names, stored under
 * {@code Documents/MiLector/biblioteca.json}) is imported automatically so that no
 * reading progress is lost; the old file itself is left untouched.
 */
public class DataManager {

    public static final String LIBRARY_FOLDER = AppDirectories.LIBRARY_FOLDER;
    public static final String LIBRARY_FILE = "library.json";
    private static final String PATH_PROPERTY = "reader.library";

    /** Root structure of the JSON file. Must be static so that Gson can instantiate it. */
    private static class LibraryData {
        @SerializedName(value = "collections", alternate = {"colecciones"})
        List<BookCollection> collections = new ArrayList<>();

        @SerializedName(value = "books", alternate = {"libros"})
        List<Book> books = new ArrayList<>();

        @SerializedName(value = "preferences", alternate = {"preferencias"})
        Preferences preferences = new Preferences();
    }

    private final Path filePath;
    private final boolean importLegacy;
    private final Gson gson;
    private LibraryData data;

    /** Uses the default location and imports a legacy library on the first run. */
    public DataManager() {
        this(defaultPath(), true);
    }

    /** Uses an explicit file (tests, custom setups); no legacy import is attempted. */
    public DataManager(Path filePath) {
        this(filePath, false);
    }

    private DataManager(Path filePath, boolean importLegacy) {
        this.filePath = filePath;
        this.importLegacy = importLegacy;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.data = new LibraryData();
        load();
    }

    // ------------------------------------------------------------------
    // Location of the library file
    // ------------------------------------------------------------------

    /** Default location of the library file (see the class description). */
    public static Path defaultPath() {
        String configured = System.getProperty(PATH_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured);
        }
        return AppDirectories.libraryFolder().resolve(LIBRARY_FILE);
    }

    /** Library files written by earlier versions, in order of preference. */
    static List<Path> legacyCandidates() {
        Path home = Paths.get(System.getProperty("user.home"));
        Set<Path> candidates = new LinkedHashSet<>();
        Path documents = AppDirectories.documentsFolder();
        if (documents != null) candidates.add(documents.resolve("MiLector").resolve("biblioteca.json"));
        candidates.add(home.resolve("OneDrive").resolve("Documentos").resolve("MiLector").resolve("biblioteca.json"));
        candidates.add(home.resolve("OneDrive").resolve("Documents").resolve("MiLector").resolve("biblioteca.json"));
        candidates.add(home.resolve("Documents").resolve("MiLector").resolve("biblioteca.json"));
        return new ArrayList<>(candidates);
    }

    public Path getFilePath() {
        return filePath;
    }

    // ------------------------------------------------------------------
    // Loading and saving
    // ------------------------------------------------------------------

    public final void load() {
        if (Files.exists(filePath)) {
            data = read(filePath);
            return;
        }
        // First run: import the library of an earlier version if one exists.
        Optional<Path> legacy = importLegacy
                ? legacyCandidates().stream().filter(Files::isRegularFile).findFirst()
                : Optional.empty();
        if (legacy.isPresent()) {
            data = read(legacy.get());
            if (save()) {
                System.out.println("Imported the existing library from " + legacy.get());
            }
            return;
        }
        data = new LibraryData();
    }

    private LibraryData read(Path source) {
        try {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            LibraryData parsed = content.isBlank() ? null : gson.fromJson(content, LibraryData.class);
            return normalize(parsed);
        } catch (IOException | JsonParseException e) {
            System.err.println("Could not read the library (" + e.getMessage() + "); a copy is kept and a new one is started.");
            backupCorruptFile(source);
            return new LibraryData();
        }
    }

    private static LibraryData normalize(LibraryData parsed) {
        LibraryData d = parsed == null ? new LibraryData() : parsed;
        if (d.collections == null) d.collections = new ArrayList<>();
        if (d.books == null) d.books = new ArrayList<>();
        if (d.preferences == null) d.preferences = new Preferences();
        d.collections.removeIf(c -> c == null || c.getId() == null);
        d.books.removeIf(b -> b == null || b.getId() == null || b.getCollectionId() == null);
        // Books saved by earlier versions carry no format: derive it once from the file name.
        for (Book b : d.books) {
            if (b.getRawFormat() == null) b.setFormat(Book.formatOf(b.getFilePath()));
        }
        normalizeCollectionOrder(d.collections);
        return d;
    }

    /**
     * Sorts the collections into their display order (pinned first, then by position) and
     * renumbers them 1..n. Collections without a position (older files, or new ones) keep
     * their relative order and go after the positioned ones.
     */
    private static void normalizeCollectionOrder(List<BookCollection> collections) {
        Comparator<BookCollection> byPinned = Comparator.comparing((BookCollection c) -> !c.isPinned());
        Comparator<BookCollection> byOrder = Comparator.comparingInt(c -> c.getOrder() > 0 ? c.getOrder() : Integer.MAX_VALUE);
        collections.sort(byPinned.thenComparing(byOrder)); // List.sort is stable
        renumber(collections);
    }

    /** Assigns positions 1..n following the current order of the list. */
    private static void renumber(List<BookCollection> collections) {
        for (int i = 0; i < collections.size(); i++) {
            collections.get(i).setOrder(i + 1);
        }
    }

    private static void backupCorruptFile(Path source) {
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.copy(source, source.resolveSibling("library.corrupt-" + stamp + ".json"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // If even the copy fails there is nothing else to do.
        }
    }

    /** Creates the library file (and its folder) if it does not exist yet. */
    public void saveIfMissing() {
        if (!Files.exists(filePath)) {
            save();
        }
    }

    /** Saves the library. Returns {@code false} if the file could not be written. */
    public synchronized boolean save() {
        String json = gson.toJson(data);
        IOException lastError = null;
        // Cloud sync clients (OneDrive) may briefly lock the file while syncing: retry.
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                writeAtomically(json);
                return true;
            } catch (IOException e) {
                lastError = e;
                try {
                    Thread.sleep(120L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        System.err.println("Error saving the library: " + (lastError == null ? "unknown" : lastError.getMessage()));
        return false;
    }

    private void writeAtomically(String json) throws IOException {
        Path folder = filePath.toAbsolutePath().getParent();
        if (folder != null) Files.createDirectories(folder);
        Path temp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        try {
            Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ------------------------------------------------------------------
    // Preferences
    // ------------------------------------------------------------------

    public Preferences getPreferences() {
        return data.preferences;
    }

    // ------------------------------------------------------------------
    // Collections
    // ------------------------------------------------------------------

    /** All collections in display order: pinned ones first, then by their custom position. */
    public List<BookCollection> getCollections() {
        return List.copyOf(data.collections);
    }

    public Optional<BookCollection> findCollection(String id) {
        return data.collections.stream().filter(c -> Objects.equals(c.getId(), id)).findFirst();
    }

    /** Appends the collection at the end of the (unpinned) list and saves. */
    public void addCollection(BookCollection collection) {
        collection.setPinned(false);
        collection.setOrder(data.collections.size() + 1);
        data.collections.add(collection);
        normalizeCollectionOrder(data.collections);
        save();
    }

    public void renameCollection(BookCollection collection, String newTitle) {
        collection.setTitle(newTitle);
        save();
    }

    /** Removes the collection and all its books from the library (the .epub files are not deleted). */
    public void deleteCollection(BookCollection collection) {
        data.books.removeIf(b -> Objects.equals(b.getCollectionId(), collection.getId()));
        data.collections.removeIf(c -> Objects.equals(c.getId(), collection.getId()));
        normalizeCollectionOrder(data.collections);
        save();
    }

    /**
     * Pins or unpins a collection. A pinned collection joins the end of the pinned group
     * (listed first); an unpinned one stays where it was on screen, which now means right
     * after the pinned group. Relative order among the other collections is kept.
     */
    public void setCollectionPinned(BookCollection collection, boolean pinned) {
        collection.setPinned(pinned);
        normalizeCollectionOrder(data.collections);
        save();
    }

    /** Whether {@link #moveCollection} would actually move the collection. */
    public boolean canMoveCollection(BookCollection collection, int offset) {
        return moveTarget(collection, offset) >= 0;
    }

    /**
     * Moves the collection {@code offset} positions (negative = upwards) inside its group
     * (pinned or regular): a collection never crosses the pinned boundary this way.
     * Returns the new index in the display order, or -1 if nothing moved.
     */
    public int moveCollection(BookCollection collection, int offset) {
        int target = moveTarget(collection, offset);
        if (target < 0) return -1;
        BookCollection removed = null;
        for (BookCollection c : data.collections) {
            if (Objects.equals(c.getId(), collection.getId())) removed = c;
        }
        data.collections.remove(removed);
        data.collections.add(target, removed);
        // The list is now in the wanted display order: renumber without re-sorting.
        renumber(data.collections);
        save();
        return target;
    }

    private int moveTarget(BookCollection collection, int offset) {
        int index = -1;
        for (int i = 0; i < data.collections.size(); i++) {
            if (Objects.equals(data.collections.get(i).getId(), collection.getId())) {
                index = i;
                break;
            }
        }
        if (index < 0 || offset == 0) return -1;
        // The list is kept in display order, so the pinned group is a prefix of it.
        int pinnedCount = (int) data.collections.stream().filter(BookCollection::isPinned).count();
        int groupStart = collection.isPinned() ? 0 : pinnedCount;
        int groupEnd = collection.isPinned() ? pinnedCount - 1 : data.collections.size() - 1;
        int target = Math.max(groupStart, Math.min(groupEnd, index + offset));
        return target == index ? -1 : target;
    }

    /**
     * Whether the collection contains at least one book and every book has the given
     * format. Empty and mixed collections are exclusive to no format.
     */
    public boolean hasOnlyFormat(BookCollection collection, String format) {
        List<Book> books = getBooksOf(collection.getId());
        return !books.isEmpty() && books.stream().allMatch(b -> b.getFormat().equalsIgnoreCase(format));
    }

    public int countBooks(BookCollection collection) {
        return (int) data.books.stream().filter(b -> Objects.equals(b.getCollectionId(), collection.getId())).count();
    }

    public int countRead(BookCollection collection) {
        return (int) data.books.stream()
                .filter(b -> Objects.equals(b.getCollectionId(), collection.getId()) && b.isRead())
                .count();
    }

    // ------------------------------------------------------------------
    // Books
    // ------------------------------------------------------------------

    public List<Book> getBooks() {
        return data.books;
    }

    /** Books of a collection sorted by their custom order number. */
    public List<Book> getBooksOf(String collectionId) {
        List<Book> filtered = new ArrayList<>();
        for (Book book : data.books) {
            if (Objects.equals(book.getCollectionId(), collectionId)) {
                filtered.add(book);
            }
        }
        filtered.sort(Comparator.comparingInt(Book::getOrder).thenComparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER));
        return filtered;
    }

    public boolean hasBookWithPath(String collectionId, String path) {
        Path wanted = Paths.get(path).toAbsolutePath().normalize();
        return getBooksOf(collectionId).stream()
                .anyMatch(b -> b.getFilePath() != null
                        && Paths.get(b.getFilePath()).toAbsolutePath().normalize().equals(wanted));
    }

    /** Appends the book to its collection and saves. */
    public void addBook(Book book) {
        data.books.add(book);
        normalizeOrder(book.getCollectionId());
        save();
    }

    /** Adds several books at once (a single write to disk). */
    public void addBooks(List<Book> books) {
        if (books.isEmpty()) return;
        data.books.addAll(books);
        normalizeOrder(books.get(0).getCollectionId());
        save();
    }

    public void deleteBook(Book book) {
        data.books.removeIf(b -> Objects.equals(b.getId(), book.getId()));
        normalizeOrder(book.getCollectionId());
        save();
    }

    /**
     * Moves the book {@code offset} positions (negative = upwards).
     * Returns the new index inside the collection, or -1 if nothing moved.
     */
    public int moveBook(Book book, int offset) {
        List<Book> list = getBooksOf(book.getCollectionId());
        int index = -1;
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i).getId(), book.getId())) {
                index = i;
                break;
            }
        }
        if (index < 0) return -1;
        int target = Math.max(0, Math.min(list.size() - 1, index + offset));
        if (target == index) return -1;
        list.remove(index);
        list.add(target, book);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setOrder(i + 1);
        }
        save();
        return target;
    }

    /** Makes the order numbers consecutive (1, 2, 3...) without changing the relative order. */
    private void normalizeOrder(String collectionId) {
        List<Book> list = getBooksOf(collectionId);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setOrder(i + 1);
        }
    }

    public int nextOrder(String collectionId) {
        return getBooksOf(collectionId).stream().mapToInt(Book::getOrder).max().orElse(0) + 1;
    }

    /** The most recently opened book that is not finished yet, if any. */
    public Optional<Book> lastReadBook() {
        return data.books.stream()
                .filter(b -> b.getLastReadAt() > 0 && !b.isRead())
                .max(Comparator.comparingLong(Book::getLastReadAt));
    }
}
