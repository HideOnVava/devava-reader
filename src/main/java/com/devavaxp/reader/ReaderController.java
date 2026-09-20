package com.devavaxp.reader;

import com.devavaxp.reader.bridge.JsBridge;
import com.devavaxp.reader.data.AppDirectories;
import com.devavaxp.reader.data.DataManager;
import com.devavaxp.reader.epub.BookSearch;
import com.devavaxp.reader.epub.ChapterText;
import com.devavaxp.reader.epub.EpubBook;
import com.devavaxp.reader.epub.EpubBook.TocEntry;
import com.devavaxp.reader.epub.EpubExtractor;
import com.devavaxp.reader.epub.EpubParser;
import com.devavaxp.reader.model.Book;
import com.devavaxp.reader.model.BookCollection;
import com.devavaxp.reader.model.Bookmark;
import com.devavaxp.reader.model.Preferences;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import netscape.javascript.JSException;
import netscape.javascript.JSObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reading screen. The WebView shows one chapter (one spine file) at a time; pagination
 * inside the chapter is done by {@code reader.js}, while this controller takes care of
 * switching chapters, keyboard and mouse input, the table of contents, the settings
 * popup and saving the reading progress.
 */
public class ReaderController implements Navigator.Screen, JsBridge.Listener {

    /** Where to position inside a chapter once it has loaded. */
    private enum Target { START, END, FRACTION, FRAGMENT, MATCH }

    /** Reading themes: colors for the content and CSS class for the surrounding UI. */
    private enum Theme {
        LIGHT(Preferences.THEME_LIGHT, "theme-light", "#FAFAF9", "#1C1917", "#2563EB", false),
        SEPIA(Preferences.THEME_SEPIA, "theme-sepia", "#F4ECD8", "#4A3B2A", "#8B5A2B", false),
        DARK(Preferences.THEME_DARK, "theme-dark", "#121212", "#D6D3D1", "#93C5FD", true);

        final String name;
        final String cssClass;
        final String background;
        final String text;
        final String link;
        final boolean forceColors;

        Theme(String name, String cssClass, String background, String text, String link, boolean forceColors) {
            this.name = name;
            this.cssClass = cssClass;
            this.background = background;
            this.text = text;
            this.link = link;
            this.forceColors = forceColors;
        }

        static Theme of(String name) {
            for (Theme t : values()) {
                if (t.name.equalsIgnoreCase(name)) return t;
            }
            return LIGHT;
        }
    }

    private record ViewState(int view, int total) {
    }

    // Web font stacks: the first family present on the system wins, so each platform gets a
    // good match (Windows, macOS, then common Linux fonts, then the generic family).
    private static final String FONT_SERIF = "Georgia, \"Times New Roman\", \"Noto Serif\", \"Liberation Serif\", \"DejaVu Serif\", serif";
    private static final String FONT_SANS = "\"Segoe UI\", \"Helvetica Neue\", Arial, \"Noto Sans\", \"Liberation Sans\", \"DejaVu Sans\", sans-serif";
    private static final String HINT = "← →  page  ·  B bookmark  ·  T contents  ·  "
            + AppDirectories.shortcutKey() + "+F search  ·  Aa settings  ·  F11 full screen";
    private static final String END_HINT = "End of book  ·  marked as read";
    private static final int EXCERPT_LENGTH = 140;
    private static final int MAX_HITS = 300;
    private static final int HIT_CONTEXT = 40;

    @FXML private BorderPane root;
    @FXML private Button historyBackButton;
    @FXML private Button bookmarkButton;
    @FXML private Button tocButton;
    @FXML private Button settingsButton;
    @FXML private Button fullScreenButton;
    @FXML private Label titleLabel;
    @FXML private Label chapterLabel;
    @FXML private Label pageLabel;
    @FXML private Label hintLabel;
    @FXML private Label progressLabel;
    @FXML private Label loadingLabel;
    @FXML private VBox tocPanel;
    @FXML private HBox panelTabs;
    @FXML private ListView<TocEntry> tocList;
    @FXML private ListView<Bookmark> bookmarkList;
    @FXML private VBox searchBox;
    @FXML private TextField searchField;
    @FXML private ListView<BookSearch.Hit> searchList;
    @FXML private Label searchStatus;
    @FXML private StackPane viewerContainer;
    @FXML private WebView webView;
    @FXML private ProgressBar progressBar;

    private Navigator navigator;
    private DataManager dataManager;
    private Preferences prefs;
    private Book book;
    private WebEngine engine;
    private JsBridge bridge; // strong reference required: the WebView only holds it weakly
    private String engineScript;

    private EpubBook epub = EpubBook.empty();
    private Path cacheDir;
    private int chapter = 0;
    private boolean loading = false;
    private boolean closed = false;
    private boolean endOfBookShown = false;
    private int locationReloads = 0;

    private Target target = Target.START;
    private double targetFraction = 0.0;
    private String targetFragment = null;
    private BookSearch.Hit targetMatch = null;

    /** Chapter texts prepared for searching, built on the first search. */
    private List<BookSearch.Chapter> searchIndex;
    private ExecutorService searchPool;
    private PauseTransition searchDebounce;
    private int searchGeneration = 0;

    /** Positions from before following a link or a table-of-contents entry: {chapter, fraction}. */
    private final Deque<double[]> history = new ArrayDeque<>();

    private EventHandler<KeyEvent> keyFilter;
    private PauseTransition pendingSave;
    private PauseTransition hintReset;
    private Popup settingsPopup;
    private Label fontSizeValue;
    private ReaderSidePanel sidePanel;
    private BookmarksPane bookmarks;
    private boolean wasMaximized;
    private double wheelAccumulated = 0;
    private long lastWheelFlip = 0;

    // ------------------------------------------------------------------
    // Initialization
    // ------------------------------------------------------------------

    public void init(Navigator navigator, Book book) {
        this.navigator = navigator;
        this.dataManager = navigator.getDataManager();
        this.prefs = dataManager.getPreferences();
        this.book = book;
        this.engineScript = readResource("reader.js");
        this.engine = webView.getEngine();
        this.bridge = new JsBridge(this);

        titleLabel.setText(book.getTitle());
        chapterLabel.setText("Preparing…");
        pageLabel.setText("");
        progressLabel.setText("");
        hintLabel.setText("");
        webView.setOpacity(0);
        loadingLabel.setVisible(true);
        fullScreenButton.setText("");
        fullScreenButton.setGraphic(UiControls.fullScreenIcon());

        applyUiTheme();
        setupWebEngine();
        setupInput();
        setupToc();
        setupSearch();
        sidePanel = new ReaderSidePanel(tocPanel, panelTabs, () -> webView.requestFocus(),
                new ReaderSidePanel.TabView(ReaderSidePanel.Tab.CONTENTS, tocList, tocList),
                new ReaderSidePanel.TabView(ReaderSidePanel.Tab.BOOKMARKS, bookmarkList, bookmarkList),
                new ReaderSidePanel.TabView(ReaderSidePanel.Tab.SEARCH, searchBox, searchField));
        bookmarks = new BookmarksPane(book, bookmarkList, new BookmarksPane.Host() {
            @Override public String describe(Bookmark b) { return describeBookmark(b); }
            @Override public void goTo(Bookmark b) { goToBookmark(b); }
            @Override public void changed() { updateBookmarkButton(); scheduleSave(); }
            @Override public Stage window() { return navigator.getStage(); }
        });

        Stage stage = navigator.getStage();
        wasMaximized = stage.isMaximized();
        if (!wasMaximized && !stage.isFullScreen()) {
            stage.setMaximized(true);
        }
        stage.setFullScreenExitHint("");
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);

        // Extraction and parsing happen off the UI thread; the WebView stays hidden meanwhile.
        Thread worker = new Thread(() -> {
            try {
                Path dir = EpubExtractor.extract(Paths.get(book.getFilePath()), book.getId());
                EpubBook parsed = EpubParser.parse(dir);
                Platform.runLater(() -> onBookReady(dir, parsed));
            } catch (Exception e) {
                Platform.runLater(() -> onLoadError(e));
            }
        }, "epub-extract");
        worker.setDaemon(true);
        worker.start();
    }

    private static String readResource(String name) {
        try (InputStream in = ReaderController.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("Missing resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + name, e);
        }
    }

    private void onBookReady(Path dir, EpubBook parsed) {
        if (closed) {
            EpubExtractor.deleteDirectory(dir);
            return;
        }
        cacheDir = dir;
        epub = parsed;
        if (epub.isEmpty()) {
            onLoadError(new IOException("The EPUB contains no readable chapters."));
            return;
        }
        tocList.getItems().setAll(epub.getToc());
        sidePanel.setAvailable(ReaderSidePanel.Tab.CONTENTS, !epub.getToc().isEmpty());

        int start = Math.min(book.getSavedChapter(), epub.chapterCount() - 1);
        double fraction = book.getSavedFraction();
        if (book.isRead() && book.getReadingPercentage() >= 99.9) {
            // A finished book is opened again from the beginning.
            start = 0;
            fraction = 0.0;
        }
        targetFraction = fraction;
        loadChapter(start, fraction > 0 ? Target.FRACTION : Target.START);
    }

    private void onLoadError(Exception e) {
        if (closed) return;
        e.printStackTrace();
        loadingLabel.setVisible(false);
        Dialogs.error(navigator.getStage(), "Couldn't open the book",
                "File: " + book.getFilePath() + "\n\n" + e.getMessage());
        onBack();
    }

    // ------------------------------------------------------------------
    // WebView
    // ------------------------------------------------------------------

    private void setupWebEngine() {
        webView.setContextMenuEnabled(false);
        engine.setJavaScriptEnabled(true);
        engine.setUserStyleSheetLocation(baseStylesheet());
        engine.setOnAlert(ev -> { });
        engine.setOnError(ev -> System.err.println("WebView: " + ev.getMessage()));
        // The document title may become available slightly after the load completes.
        engine.titleProperty().addListener((obs, previous, title) -> {
            if (!closed && !loading && !epub.isEmpty()) {
                chapterLabel.setText(epub.chapterTitle(chapter, title, book.getTitle()));
            }
        });
        engine.getLoadWorker().stateProperty().addListener((obs, previous, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                onLoadComplete();
            } else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
                loading = false;
                loadingLabel.setVisible(false);
                webView.setOpacity(1);
            }
        });
    }

    /** User stylesheet: prevents white flashes and scroll bars before pagination kicks in. */
    private String baseStylesheet() {
        Theme t = Theme.of(prefs.getTheme());
        String css = "html{background:" + t.background + " !important;overflow:hidden !important;}"
                + "body{background:" + t.background + " !important;color:" + t.text + " !important;}";
        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    private void loadChapter(int index, Target newTarget) {
        if (epub.isEmpty()) return;
        chapter = Math.max(0, Math.min(epub.chapterCount() - 1, index));
        target = newTarget;
        loading = true;
        endOfBookShown = false;
        webView.setOpacity(0);
        engine.load(epub.getSpine().get(chapter).file().toUri().toString());
    }

    private void onLoadComplete() {
        if (closed || epub.isEmpty()) return;
        String location = engine.getLocation();
        Path current = pathOfLocation(location);
        int currentIndex = current == null ? -1 : epub.indexOfFile(current);
        if (currentIndex < 0) {
            // Unknown document (not part of the book): go back to the expected chapter,
            // but only once, to avoid a loop.
            if (locationReloads++ < 1) {
                loadChapter(chapter, target);
                return;
            }
        } else if (currentIndex != chapter) {
            // A link slipped past the JavaScript interception: adopt the chapter that loaded.
            chapter = currentIndex;
            int hash = location.indexOf('#');
            targetFragment = hash >= 0 ? EpubParser.decode(location.substring(hash + 1)) : null;
            target = targetFragment == null ? Target.START : Target.FRAGMENT;
        }
        locationReloads = 0;
        try {
            JSObject window = (JSObject) engine.executeScript("window");
            window.setMember("readerBridge", bridge);
            engine.executeScript(engineScript);
            JSObject reader = (JSObject) engine.executeScript("window.__reader");
            reader.call("configure", engineConfig(), false);
            switch (target) {
                case END -> reader.call("goToEnd");
                case FRACTION -> reader.call("goToFraction", targetFraction);
                case FRAGMENT -> {
                    Object ok = targetFragment == null ? Boolean.FALSE : reader.call("goToFragment", targetFragment);
                    if (!Boolean.TRUE.equals(ok)) reader.call("goToStart");
                }
                case MATCH -> {
                    Object ok = targetMatch == null ? Boolean.FALSE
                            : reader.call("goToMatch", targetMatch.match(), targetMatch.before(), targetMatch.after());
                    if (!Boolean.TRUE.equals(ok)) reader.call("goToStart");
                }
                default -> reader.call("goToStart");
            }
        } catch (JSException e) {
            System.err.println("Pagination engine error: " + e.getMessage());
        }
        target = Target.START;
        targetFragment = null;
        targetMatch = null;
        loading = false;
        loadingLabel.setVisible(false);
        webView.setOpacity(1);
        updateState();
        // While the side panel is open (browsing search results) the focus stays there.
        if (!sidePanel.isVisible()) webView.requestFocus();
    }

    private static Path pathOfLocation(String location) {
        if (location == null || !location.startsWith("file:")) return null;
        String withoutFragment = location;
        int hash = withoutFragment.indexOf('#');
        if (hash >= 0) withoutFragment = withoutFragment.substring(0, hash);
        int query = withoutFragment.indexOf('?');
        if (query >= 0) withoutFragment = withoutFragment.substring(0, query);
        try {
            return Paths.get(new URI(withoutFragment));
        } catch (Exception e) {
            return null;
        }
    }

    private String engineConfig() {
        Theme t = Theme.of(prefs.getTheme());
        JsonObject o = new JsonObject();
        o.addProperty("columns", prefs.getColumns());
        o.addProperty("fontSize", prefs.getFontSize());
        o.addProperty("fontFamily", Preferences.TYPEFACE_SANS.equalsIgnoreCase(prefs.getTypeface()) ? FONT_SANS : FONT_SERIF);
        o.addProperty("background", t.background);
        o.addProperty("textColor", t.text);
        o.addProperty("linkColor", t.link);
        o.addProperty("forceColors", t.forceColors);
        o.addProperty("marginH", 48);
        o.addProperty("marginV", 36);
        o.addProperty("edgeClicks", true);
        return o.toString();
    }

    private boolean engineReady() {
        if (loading || epub.isEmpty()) return false;
        try {
            return Boolean.TRUE.equals(engine.executeScript("typeof window.__reader === 'object'"));
        } catch (JSException e) {
            return false;
        }
    }

    private Object reader(String function, Object... args) {
        try {
            JSObject r = (JSObject) engine.executeScript("window.__reader");
            return r.call(function, args);
        } catch (JSException | ClassCastException e) {
            System.err.println("Error calling __reader." + function + ": " + e.getMessage());
            return null;
        }
    }

    private ViewState readState() {
        Object json = reader("state");
        if (json instanceof String s) {
            try {
                JsonObject o = JsonParser.parseString(s).getAsJsonObject();
                return new ViewState(o.get("view").getAsInt(), Math.max(1, o.get("total").getAsInt()));
            } catch (RuntimeException ignored) {
                // fall through to the default state
            }
        }
        return new ViewState(0, 1);
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    private void nextPage() {
        if (!engineReady()) return;
        if (Boolean.TRUE.equals(reader("next"))) {
            updateState();
        } else if (chapter < epub.chapterCount() - 1) {
            loadChapter(chapter + 1, Target.START);
        } else {
            showEndOfBook();
        }
    }

    private void previousPage() {
        if (!engineReady()) return;
        if (Boolean.TRUE.equals(reader("previous"))) {
            updateState();
        } else if (chapter > 0) {
            loadChapter(chapter - 1, Target.END);
        }
    }

    private void nextChapter() {
        if (!engineReady()) return;
        if (chapter < epub.chapterCount() - 1) loadChapter(chapter + 1, Target.START);
    }

    private void previousChapter() {
        if (!engineReady()) return;
        if (chapter > 0) loadChapter(chapter - 1, Target.START);
    }

    /**
     * Reached by trying to turn the page on the last view of the last chapter, i.e. when
     * the reader has truly finished the book (not by jumping from a link or the contents).
     */
    private void showEndOfBook() {
        if (!book.isRead()) {
            book.setRead(true);
            book.setReadingPercentage(100.0);
            scheduleSave();
        }
        if (endOfBookShown) return;
        endOfBookShown = true;
        hintLabel.setText(END_HINT);
    }

    private void pushHistory() {
        ViewState s = readState();
        double fraction = s.total() > 1 ? (double) s.view() / (s.total() - 1) : 0.0;
        history.push(new double[]{chapter, fraction});
        if (history.size() > 50) history.removeLast();
        updateHistoryButton();
    }

    private void goBackInHistory() {
        if (history.isEmpty() || !engineReady()) return;
        double[] pos = history.pop();
        updateHistoryButton();
        int ch = (int) pos[0];
        targetFraction = pos[1];
        if (ch == chapter) {
            reader("goToFraction", targetFraction);
            updateState();
        } else {
            loadChapter(ch, Target.FRACTION);
        }
    }

    private void updateHistoryButton() {
        boolean any = !history.isEmpty();
        historyBackButton.setVisible(any);
        historyBackButton.setManaged(any);
    }

    private void goToTocEntry(TocEntry entry) {
        if (entry == null || !engineReady()) return;
        pushHistory();
        if (entry.spineIndex() == chapter) {
            boolean ok = entry.fragment() != null && Boolean.TRUE.equals(reader("goToFragment", entry.fragment()));
            if (!ok) reader("goToStart");
            updateState();
        } else {
            targetFragment = entry.fragment();
            loadChapter(entry.spineIndex(), entry.fragment() == null ? Target.START : Target.FRAGMENT);
        }
    }

    // ------------------------------------------------------------------
    // JavaScript -> Java bridge
    // ------------------------------------------------------------------

    @Override
    public void onEvent(String event, String data) {
        if (closed) return;
        switch (event) {
            case "next" -> nextPage();
            case "previous" -> previousPage();
            case "state" -> { if (!loading) updateState(); }
            default -> { }
        }
    }

    @Override
    public void onLink(String absoluteHref, String originalHref) {
        if (closed || loading) return;
        String href = (absoluteHref == null || absoluteHref.isEmpty()) ? originalHref : absoluteHref;
        if (href == null || href.isEmpty()) return;
        String lower = href.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:")) {
            try {
                navigator.getHostServices().showDocument(href);
            } catch (RuntimeException e) {
                System.err.println("Could not open the external link: " + e.getMessage());
            }
            return;
        }
        if (!lower.startsWith("file:")) return;

        String fragment = null;
        int hash = href.indexOf('#');
        if (hash >= 0) {
            fragment = EpubParser.decode(href.substring(hash + 1));
            href = href.substring(0, hash);
        }
        Path file = pathOfLocation(href);
        int index = file == null ? -1 : epub.indexOfFile(file);
        if (index < 0) return;

        if (index == chapter) {
            // Inside the same chapter only a jump to an existing fragment makes sense.
            if (fragment == null) return;
            ViewState before = readState();
            if (!Boolean.TRUE.equals(reader("goToFragment", fragment))) return;
            double fraction = before.total() > 1 ? (double) before.view() / (before.total() - 1) : 0.0;
            history.push(new double[]{chapter, fraction});
            updateHistoryButton();
            updateState();
        } else {
            pushHistory();
            targetFragment = fragment;
            loadChapter(index, fragment == null ? Target.START : Target.FRAGMENT);
        }
    }

    // ------------------------------------------------------------------
    // State, progress and saving
    // ------------------------------------------------------------------

    private void updateState() {
        if (closed || epub.isEmpty()) return;
        ViewState s = readState();
        int total = Math.max(1, s.total());
        int view = Math.max(0, Math.min(total - 1, s.view()));

        pageLabel.setText("Page " + (view + 1) + " of " + total
                + "   ·   Chapter " + (chapter + 1) + " of " + epub.chapterCount());
        double fractionToSave = total > 1 ? (double) view / (total - 1) : 0.0;
        double fractionRead = (view + 1.0) / total;
        double progress = epub.progress(chapter, fractionRead);
        progressBar.setProgress(progress);
        progressLabel.setText(String.format(Locale.ROOT, "%.1f %%", progress * 100.0));
        chapterLabel.setText(epub.chapterTitle(chapter, engine.getTitle(), book.getTitle()));
        if (!endOfBookShown) {
            hintLabel.setText(HINT);
        }
        highlightCurrentChapterInToc();
        updateBookmarkButton();

        book.setPosition(chapter, fractionToSave);
        book.setReadingPercentage(progress * 100.0);
        book.setLastReadAt(System.currentTimeMillis());
        scheduleSave();
    }

    private void scheduleSave() {
        if (pendingSave == null) {
            pendingSave = new PauseTransition(Duration.seconds(1.5));
            pendingSave.setOnFinished(ev -> dataManager.save());
        }
        pendingSave.playFromStart();
    }

    private void saveNow() {
        if (pendingSave != null) pendingSave.stop();
        dataManager.save();
    }

    // ------------------------------------------------------------------
    // Keyboard and mouse
    // ------------------------------------------------------------------

    private void setupInput() {
        keyFilter = this::onKey;
        navigator.getScene().addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);

        webView.addEventFilter(ScrollEvent.SCROLL, ev -> {
            ev.consume();
            if (ev.isInertia()) return;
            wheelAccumulated += ev.getDeltaY();
            long now = System.currentTimeMillis();
            if (Math.abs(wheelAccumulated) >= 30 && now - lastWheelFlip > 160) {
                if (wheelAccumulated < 0) nextPage(); else previousPage();
                wheelAccumulated = 0;
                lastWheelFlip = now;
            }
        });
        // Side mouse buttons (back/forward)
        webView.addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> {
            if (ev.getButton() == MouseButton.BACK) { previousPage(); ev.consume(); }
            else if (ev.getButton() == MouseButton.FORWARD) { nextPage(); ev.consume(); }
        });
    }

    private void onKey(KeyEvent ev) {
        if (closed) return;
        if (settingsPopup != null && settingsPopup.isShowing()) return;
        Node focused = navigator.getScene().getFocusOwner();
        boolean ctrl = ev.isControlDown() || ev.isShortcutDown();
        if (ev.getCode() == KeyCode.F && ctrl) {
            openSearch();
            ev.consume();
            return;
        }
        boolean focusInToc = tocPanel.isVisible() && focused != null && isDescendant(focused, tocPanel);
        if (focusInToc) {
            boolean typing = focused == searchField;
            if (ev.getCode() == KeyCode.ESCAPE || (ev.getCode() == KeyCode.T && !typing)) {
                showToc(false);
                ev.consume();
            } else if (ev.getCode() == KeyCode.TAB) {
                sidePanel.switchToOther(); // Contents -> Bookmarks -> Search
                ev.consume();
            }
            return; // arrows, Enter, N and Delete are handled by the lists and the field themselves
        }
        switch (ev.getCode()) {
            case RIGHT -> { if (ctrl) nextChapter(); else nextPage(); ev.consume(); }
            case LEFT -> { if (ctrl) previousChapter(); else if (ev.isAltDown()) goBackInHistory(); else previousPage(); ev.consume(); }
            case PAGE_DOWN, DOWN -> { nextPage(); ev.consume(); }
            case PAGE_UP, UP -> { previousPage(); ev.consume(); }
            case SPACE -> { if (ev.isShiftDown()) previousPage(); else nextPage(); ev.consume(); }
            case HOME -> {
                if (!engineReady()) return;
                if (ctrl) loadChapter(0, Target.START); else { reader("goToStart"); updateState(); }
                ev.consume();
            }
            case END -> {
                if (!engineReady()) return;
                if (ctrl) loadChapter(epub.chapterCount() - 1, Target.END); else { reader("goToEnd"); updateState(); }
                ev.consume();
            }
            case BACK_SPACE -> { goBackInHistory(); ev.consume(); }
            case ESCAPE -> {
                if (tocPanel.isVisible()) showToc(false);
                else if (navigator.getStage().isFullScreen()) navigator.getStage().setFullScreen(false);
                else onBack();
                ev.consume();
            }
            case F11 -> { toggleFullScreen(); ev.consume(); }
            case T -> { if (!ctrl) { toggleToc(); ev.consume(); } }
            case B -> { if (!ctrl) { toggleBookmark(); ev.consume(); } }
            case PLUS, ADD, EQUALS -> { if (ctrl) { changeFontSize(1); ev.consume(); } }
            case MINUS, SUBTRACT -> { if (ctrl) { changeFontSize(-1); ev.consume(); } }
            case DIGIT1, NUMPAD1 -> { if (!ctrl) { changeColumns(1); ev.consume(); } }
            case DIGIT2, NUMPAD2 -> { if (!ctrl) { changeColumns(2); ev.consume(); } }
            default -> { }
        }
    }

    private static boolean isDescendant(Node node, Node ancestor) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == ancestor) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Table of contents
    // ------------------------------------------------------------------

    private void setupToc() {
        tocList.setCellFactory(list -> {
            ListCell<TocEntry> cell = new ListCell<>() {
                @Override
                protected void updateItem(TocEntry item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("current");
                    if (empty || item == null) {
                        setText(null);
                        return;
                    }
                    setText(item.title());
                    setPadding(new Insets(6, 8, 6, 8 + 16.0 * Math.min(4, item.level())));
                    if (getIndex() == epub.tocEntryForChapter(chapter)) {
                        getStyleClass().add("current");
                    }
                }
            };
            // With prefWidth 0 the cell takes the list width and long titles wrap onto several lines.
            cell.setWrapText(true);
            cell.setPrefWidth(0);
            return cell;
        });
        tocList.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY) {
                TocEntry selected = tocList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    goToTocEntry(selected);
                    showToc(false);
                }
            }
        });
        tocList.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER) {
                TocEntry selected = tocList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    goToTocEntry(selected);
                    showToc(false);
                }
                ev.consume();
            }
        });
    }

    private void highlightCurrentChapterInToc() {
        if (tocPanel.isVisible()) tocList.refresh();
    }

    private void toggleToc() {
        showToc(!sidePanel.isVisible());
    }

    /** Opens the side panel on the contents (or on the bookmarks when there are none). */
    private void showToc(boolean show) {
        if (!show) {
            sidePanel.hide();
            return;
        }
        sidePanel.show(ReaderSidePanel.Tab.CONTENTS);
        if (sidePanel.tab() == ReaderSidePanel.Tab.CONTENTS) {
            tocList.refresh();
            int current = epub.tocEntryForChapter(chapter);
            if (current >= 0) {
                tocList.getSelectionModel().select(current);
                tocList.scrollTo(Math.max(0, current - 3));
            }
        }
    }

    // ------------------------------------------------------------------
    // Bookmarks
    // ------------------------------------------------------------------

    /** Adds a bookmark on the current page, or removes the one that is already there. */
    private void toggleBookmark() {
        if (!engineReady()) return;
        ViewState s = readState();
        double fraction = s.total() > 1 ? (double) s.view() / (s.total() - 1) : 0.0;
        Bookmark existing = book.findBookmark(chapter, fraction, pageTolerance(s));
        if (existing != null) {
            book.removeBookmark(existing);
            flashHint("Bookmark removed");
        } else {
            Object excerpt = reader("excerpt", EXCERPT_LENGTH);
            book.addBookmark(new Bookmark(chapter, fraction, excerpt instanceof String text ? text : ""));
            flashHint("Bookmark added  ·  T shows your bookmarks");
        }
        bookmarks.refresh();
        updateBookmarkButton();
        scheduleSave();
    }

    /** Half a page, in fraction units: a bookmark set anywhere on the current page matches. */
    private static double pageTolerance(ViewState s) {
        return s.total() > 1 ? 0.5 / (s.total() - 1) : 1.0;
    }

    private void updateBookmarkButton() {
        if (!engineReady()) return;
        ViewState s = readState();
        double fraction = s.total() > 1 ? (double) s.view() / (s.total() - 1) : 0.0;
        boolean marked = book.findBookmark(chapter, fraction, pageTolerance(s)) != null;
        bookmarkButton.setText(marked ? "★" : "☆");
    }

    private String describeBookmark(Bookmark b) {
        String title = epub.chapterTitle(b.getChapter(), "");
        return title.isEmpty() ? "Chapter " + (b.getChapter() + 1) : title;
    }

    private void goToBookmark(Bookmark b) {
        if (!engineReady()) return;
        pushHistory();
        showToc(false);
        if (b.getChapter() == chapter) {
            reader("goToFraction", b.getFraction());
            updateState();
        } else {
            targetFraction = b.getFraction();
            loadChapter(b.getChapter(), Target.FRACTION);
        }
    }

    // ------------------------------------------------------------------
    // Search inside the book
    // ------------------------------------------------------------------

    private void setupSearch() {
        searchStatus.setText("");
        searchDebounce = new PauseTransition(Duration.millis(250));
        searchDebounce.setOnFinished(e -> runSearch());
        searchField.textProperty().addListener((obs, previous, text) -> searchDebounce.playFromStart());
        searchField.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case ENTER -> {
                    searchDebounce.stop();
                    if (searchList.getItems().isEmpty()) runSearch(); else jumpToResult(selectedOrFirstHit());
                    ev.consume();
                }
                case DOWN -> {
                    if (!searchList.getItems().isEmpty()) {
                        if (searchList.getSelectionModel().isEmpty()) searchList.getSelectionModel().selectFirst();
                        searchList.requestFocus();
                    }
                    ev.consume();
                }
                default -> { }
            }
        });
        searchList.setPlaceholder(new Label(""));
        searchList.setCellFactory(list -> new HitCell());
        searchList.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY) jumpToResult(searchList.getSelectionModel().getSelectedItem());
        });
        searchList.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER) {
                jumpToResult(searchList.getSelectionModel().getSelectedItem());
                ev.consume();
            }
        });
    }

    /** Opens the side panel on the search tab with the field ready to type into. */
    private void openSearch() {
        sidePanel.show(ReaderSidePanel.Tab.SEARCH);
    }

    private BookSearch.Hit selectedOrFirstHit() {
        BookSearch.Hit selected = searchList.getSelectionModel().getSelectedItem();
        return selected != null ? selected : (searchList.getItems().isEmpty() ? null : searchList.getItems().get(0));
    }

    /** Searches the whole book in the background and shows the hits; stale results are dropped. */
    private void runSearch() {
        if (closed) return;
        String query = searchField.getText();
        final int generation = ++searchGeneration;
        if (BookSearch.normalizeQuery(query).isEmpty()) {
            searchList.getItems().clear();
            searchStatus.setText(query == null || query.isBlank() ? "" : "Type at least " + BookSearch.MIN_QUERY_LENGTH + " characters");
            return;
        }
        if (searchPool == null) {
            searchPool = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "book-search");
                t.setDaemon(true);
                return t;
            });
        }
        searchStatus.setText("Searching…");
        searchPool.submit(() -> {
            List<BookSearch.Hit> hits;
            try {
                hits = BookSearch.search(searchIndex(), query, MAX_HITS, HIT_CONTEXT);
            } catch (RuntimeException e) {
                System.err.println("Search failed: " + e.getMessage());
                hits = List.of();
            }
            List<BookSearch.Hit> result = hits;
            Platform.runLater(() -> {
                if (closed || generation != searchGeneration) return;
                searchList.getItems().setAll(result);
                searchStatus.setText(result.isEmpty() ? "No results"
                        : result.size() >= MAX_HITS ? MAX_HITS + "+ results (showing the first " + MAX_HITS + ")"
                        : result.size() + (result.size() == 1 ? " result" : " results"));
            });
        });
    }

    /** The chapter texts, extracted on the first search (a few hundred ms for a long novel). */
    private synchronized List<BookSearch.Chapter> searchIndex() {
        if (searchIndex == null) {
            List<BookSearch.Chapter> chapters = new ArrayList<>(epub.chapterCount());
            for (EpubBook.SpineItem item : epub.getSpine()) {
                String text;
                try {
                    text = ChapterText.extract(item.file());
                } catch (IOException | RuntimeException e) {
                    text = "";
                }
                chapters.add(new BookSearch.Chapter(text));
            }
            searchIndex = chapters;
        }
        return searchIndex;
    }

    /** Goes to a hit, keeping the panel open so that the next result is one key away. */
    private void jumpToResult(BookSearch.Hit hit) {
        if (hit == null || !engineReady()) return;
        pushHistory();
        if (hit.chapter() == chapter) {
            Object ok = reader("goToMatch", hit.match(), hit.before(), hit.after());
            if (!Boolean.TRUE.equals(ok)) reader("goToStart");
            updateState();
        } else {
            targetMatch = hit;
            loadChapter(hit.chapter(), Target.MATCH);
        }
    }

    /** Chapter on one line, the passage with the match emphasised below. */
    private final class HitCell extends ListCell<BookSearch.Hit> {
        private final Label where = new Label();
        private final Text before = new Text();
        private final Text match = new Text();
        private final Text after = new Text();
        private final TextFlow snippet = new TextFlow(before, match, after);
        private final VBox box = new VBox(2, where, snippet);

        HitCell() {
            where.getStyleClass().add("bookmark-where");
            snippet.getStyleClass().add("search-snippet");
            before.getStyleClass().add("search-context");
            after.getStyleClass().add("search-context");
            match.getStyleClass().add("search-match");
            where.maxWidthProperty().bind(searchList.widthProperty().subtract(36));
            snippet.maxWidthProperty().bind(searchList.widthProperty().subtract(36));
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(BookSearch.Hit item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            String title = epub.chapterTitle(item.chapter(), "");
            where.setText(title.isEmpty() ? "Chapter " + (item.chapter() + 1) : title);
            before.setText((item.before().isEmpty() || item.before().startsWith(" ") ? "" : "…") + item.before());
            match.setText(item.match());
            after.setText(item.after() + (item.after().isEmpty() || item.after().endsWith(".") ? "" : "…"));
            setGraphic(box);
        }
    }

    /** Shows a short confirmation in the footer, then restores the usual hint. */
    private void flashHint(String text) {
        hintLabel.setText(text);
        if (hintReset == null) {
            hintReset = new PauseTransition(Duration.seconds(2.2));
            hintReset.setOnFinished(e -> hintLabel.setText(endOfBookShown ? END_HINT : HINT));
        }
        hintReset.playFromStart();
    }

    @FXML
    private void onToggleBookmark() {
        toggleBookmark();
    }

    // ------------------------------------------------------------------
    // Settings: font size, typeface, theme and columns
    // ------------------------------------------------------------------

    private void changeFontSize(int delta) {
        int size = prefs.getFontSize() + delta;
        if (size < Preferences.FONT_MIN || size > Preferences.FONT_MAX) return;
        prefs.setFontSize(size);
        if (fontSizeValue != null) fontSizeValue.setText(String.valueOf(size));
        applySettings();
    }

    private void changeColumns(int columns) {
        if (prefs.getColumns() == columns) return;
        prefs.setColumns(columns);
        applySettings();
    }

    private void changeTheme(String theme) {
        if (prefs.getTheme().equalsIgnoreCase(theme)) return;
        prefs.setTheme(theme);
        applySettings();
    }

    private void changeTypeface(String typeface) {
        if (prefs.getTypeface().equalsIgnoreCase(typeface)) return;
        prefs.setTypeface(typeface);
        applySettings();
    }

    private void applySettings() {
        applyUiTheme();
        engine.setUserStyleSheetLocation(baseStylesheet());
        if (engineReady()) {
            reader("configure", engineConfig(), true);
            updateState();
        }
        scheduleSave();
    }

    private void applyUiTheme() {
        Theme t = Theme.of(prefs.getTheme());
        for (Theme other : Theme.values()) root.getStyleClass().remove(other.cssClass);
        root.getStyleClass().add(t.cssClass);
    }

    @FXML
    private void onSettings() {
        if (settingsPopup == null) settingsPopup = createSettingsPopup();
        if (settingsPopup.isShowing()) {
            settingsPopup.hide();
            return;
        }
        Bounds b = settingsButton.localToScreen(settingsButton.getBoundsInLocal());
        double width = 300;
        settingsPopup.show(settingsButton, b.getMaxX() - width, b.getMaxY() + 6);
    }

    private Popup createSettingsPopup() {
        VBox box = new VBox(12);
        box.getStyleClass().add("settings");
        box.setPrefWidth(300);
        box.getStylesheets().addAll(navigator.getScene().getStylesheets());

        // Font size
        Button smaller = new Button("−");
        smaller.getStyleClass().add("icon");
        fontSizeValue = new Label(String.valueOf(prefs.getFontSize()));
        fontSizeValue.getStyleClass().add("value");
        Button larger = new Button("+");
        larger.getStyleClass().add("icon");
        smaller.setOnAction(e -> changeFontSize(-1));
        larger.setOnAction(e -> changeFontSize(1));
        HBox fontRow = new HBox(8, smaller, fontSizeValue, larger);
        fontRow.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(section("FONT SIZE", fontRow));

        box.getChildren().add(section("TYPEFACE", UiControls.segmented(prefs.getTypeface(), this::changeTypeface,
                new String[][]{{Preferences.TYPEFACE_SERIF, "Serif"}, {Preferences.TYPEFACE_SANS, "Sans"}})));
        box.getChildren().add(section("THEME", UiControls.segmented(prefs.getTheme(), this::changeTheme,
                new String[][]{{Preferences.THEME_LIGHT, "Light"}, {Preferences.THEME_SEPIA, "Sepia"}, {Preferences.THEME_DARK, "Dark"}})));
        box.getChildren().add(section("COLUMNS", UiControls.segmented(String.valueOf(prefs.getColumns()),
                v -> changeColumns(Integer.parseInt(v)), new String[][]{{"1", "One"}, {"2", "Two"}})));

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.getContent().add(box);
        popup.setOnHidden(e -> webView.requestFocus());
        return popup;
    }

    private static VBox section(String name, Node content) {
        Label label = new Label(name);
        label.getStyleClass().add("setting-name");
        return new VBox(6, label, content);
    }

    // ------------------------------------------------------------------
    // Toolbar buttons
    // ------------------------------------------------------------------

    @FXML
    private void onToggleToc() {
        toggleToc();
    }

    @FXML
    private void onHistoryBack() {
        goBackInHistory();
    }

    @FXML
    private void onToggleFullScreen() {
        toggleFullScreen();
    }

    private void toggleFullScreen() {
        Stage stage = navigator.getStage();
        stage.setFullScreen(!stage.isFullScreen());
        webView.requestFocus();
    }

    @FXML
    private void onBack() {
        if (closed) return;
        BookCollection collection = dataManager.findCollection(book.getCollectionId()).orElse(null);
        if (collection != null) {
            navigator.showVolumes(collection);
        } else {
            navigator.showCollections();
        }
    }

    // ------------------------------------------------------------------
    // Shutdown
    // ------------------------------------------------------------------

    @Override
    public void onLeave() {
        if (closed) return;
        closed = true;
        saveNow();
        navigator.getScene().removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        if (settingsPopup != null) settingsPopup.hide();
        if (searchDebounce != null) searchDebounce.stop();
        if (searchPool != null) searchPool.shutdownNow();
        try {
            engine.getLoadWorker().cancel();
            engine.load("about:blank");
        } catch (RuntimeException ignored) {
            // The engine may already be released.
        }
        Stage stage = navigator.getStage();
        if (stage.isFullScreen()) stage.setFullScreen(false);
        if (!wasMaximized) stage.setMaximized(false);
        Path dir = cacheDir;
        cacheDir = null;
        if (dir != null) {
            Thread cleanup = new Thread(() -> EpubExtractor.deleteDirectory(dir), "book-cache-cleanup");
            cleanup.setDaemon(true);
            cleanup.start();
        }
    }
}
