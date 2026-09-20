package com.devavaxp.reader;

import com.devavaxp.reader.data.DataManager;
import com.devavaxp.reader.model.Book;
import com.devavaxp.reader.model.BookCollection;
import com.devavaxp.reader.model.Bookmark;
import com.devavaxp.reader.model.Preferences;
import com.devavaxp.reader.pdf.PageSource;
import com.devavaxp.reader.pdf.PageSource.OutlineEntry;
import com.devavaxp.reader.pdf.PageSource.PageSize;
import com.devavaxp.reader.pdf.PageSpreads;
import com.devavaxp.reader.pdf.PdfPageSource;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
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
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reading screen for fixed-page books (PDF). Pages are rendered to images in a background
 * thread and shown one or two at a time ("spreads"), with a choice of reading direction
 * (left-to-right or right-to-left for manga), fit mode and theme. Progress is the page.
 * <p>
 * The controller only depends on {@link PageSource}, so other fixed-page formats can be
 * added later without changing the screen.
 */
public class PdfReaderController implements Navigator.Screen {

    private static final String HINT = "← →  page  ·  B bookmark  ·  T contents  ·  ⚙ settings  ·  F11 full screen";
    private static final String END_HINT = "End of book  ·  marked as read";
    private static final double PADDING = 12;
    private static final double GAP = 6;
    private static final int WIDTH_BUCKET = 64;          // render widths are rounded up to this
    private static final long MAX_RENDER_PIXELS = 12_000_000L;
    private static final int CACHE_SIZE = 8;

    @FXML private BorderPane root;
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
    @FXML private ListView<OutlineEntry> tocList;
    @FXML private ListView<Bookmark> bookmarkList;
    @FXML private StackPane viewerContainer;
    @FXML private ScrollPane scroller;
    @FXML private HBox spreadBox;
    @FXML private ProgressBar progressBar;

    private Navigator navigator;
    private DataManager dataManager;
    private Preferences prefs;
    private Book book;

    private PageSource source;
    private List<int[]> spreads = List.of();
    private int spread = 0;
    private final ImageView[] views = {new ImageView(), new ImageView()};

    /** Single render thread: PDF rendering is not thread-safe per document and is CPU-bound. */
    private ExecutorService renderPool;
    private final Map<String, Image> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private final Set<String> pending = new HashSet<>();

    private boolean closed = false;
    private boolean ready = false;
    private boolean endOfBookShown = false;
    private EventHandler<KeyEvent> keyFilter;
    private PauseTransition pendingSave;
    private PauseTransition hintReset;
    private Popup settingsPopup;
    private ReaderSidePanel sidePanel;
    private BookmarksPane bookmarks;
    private boolean wasMaximized;
    private double wheelAccumulated = 0;
    private long lastWheelFlip = 0;
    private long lastScrollAt = 0;

    // ------------------------------------------------------------------
    // Initialization
    // ------------------------------------------------------------------

    public void init(Navigator navigator, Book book) {
        this.navigator = navigator;
        this.dataManager = navigator.getDataManager();
        this.prefs = dataManager.getPreferences();
        this.book = book;

        titleLabel.setText(book.getTitle());
        chapterLabel.setText("Preparing…");
        pageLabel.setText("");
        progressLabel.setText("");
        hintLabel.setText("");
        loadingLabel.setVisible(true);
        for (ImageView v : views) {
            v.setPreserveRatio(true);
            v.setSmooth(true);
        }

        fullScreenButton.setText("");
        fullScreenButton.setGraphic(UiControls.fullScreenIcon());
        applyUiTheme();
        setupInput();
        setupToc();
        sidePanel = new ReaderSidePanel(tocPanel, panelTabs, () -> scroller.requestFocus(),
                new ReaderSidePanel.TabView(ReaderSidePanel.Tab.CONTENTS, tocList, tocList),
                new ReaderSidePanel.TabView(ReaderSidePanel.Tab.BOOKMARKS, bookmarkList, bookmarkList));
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

        renderPool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "pdf-render");
            t.setDaemon(true);
            return t;
        });
        Thread opener = new Thread(() -> {
            try {
                PageSource opened = PdfPageSource.open(Paths.get(book.getFilePath()));
                Platform.runLater(() -> onSourceReady(opened));
            } catch (Exception e) {
                Platform.runLater(() -> onLoadError(e));
            }
        }, "pdf-open");
        opener.setDaemon(true);
        opener.start();
    }

    private void onSourceReady(PageSource opened) {
        if (closed) {
            opened.close();
            return;
        }
        source = opened;
        ready = true;
        loadingLabel.setVisible(false);
        tocList.getItems().setAll(source.outline());
        sidePanel.setAvailable(ReaderSidePanel.Tab.CONTENTS, !source.outline().isEmpty());
        rebuildSpreads();

        int startPage = Math.min(book.getSavedChapter(), source.pageCount() - 1);
        if (book.isRead() && book.getReadingPercentage() >= 99.9) {
            startPage = 0; // a finished book is opened again from the beginning
        }
        // Re-layout whenever the viewport changes (window resize, contents panel, full screen).
        scroller.viewportBoundsProperty().addListener((obs, previous, bounds) -> {
            if (ready && !closed) {
                layoutSpread();
                requestRenders();
            }
        });
        showSpread(PageSpreads.spreadOf(spreads, startPage), false);
        scroller.requestFocus();
    }

    private void onLoadError(Exception e) {
        if (closed) return;
        e.printStackTrace();
        loadingLabel.setVisible(false);
        Dialogs.error(navigator.getStage(), "Couldn't open the book",
                "File: " + book.getFilePath() + "\n\n" + e.getMessage());
        onBack();
    }

    private void rebuildSpreads() {
        spreads = PageSpreads.compute(source.pageCount(), prefs.isPdfDoublePage(),
                p -> source.pageSize(p).aspect() > 1.0);
    }

    // ------------------------------------------------------------------
    // Showing a spread
    // ------------------------------------------------------------------

    /** Shows the spread at {@code index}; {@code showEnd} scrolls to its bottom (fit-width mode). */
    private void showSpread(int index, boolean showEnd) {
        if (!ready || spreads.isEmpty()) return;
        spread = Math.max(0, Math.min(spreads.size() - 1, index));
        int[] pages = spreads.get(spread);

        // Visual order: in right-to-left books the first page of the spread goes on the right.
        spreadBox.getChildren().clear();
        List<Node> ordered = new ArrayList<>();
        for (int i = 0; i < pages.length; i++) ordered.add(views[i]);
        if (prefs.isPdfRightToLeft()) java.util.Collections.reverse(ordered);
        spreadBox.getChildren().addAll(ordered);
        for (int i = pages.length; i < views.length; i++) views[i].setImage(null);

        layoutSpread();
        requestRenders();
        Platform.runLater(() -> scroller.setVvalue(showEnd ? 1.0 : 0.0));
        updateState();
    }

    /** Sizes the image views for the current spread, sharing one scale so pages line up. */
    private void layoutSpread() {
        if (!ready || spreads.isEmpty()) return;
        Bounds viewport = scroller.getViewportBounds();
        double vw = viewport.getWidth();
        double vh = viewport.getHeight();
        if (vw <= 0 || vh <= 0) return;
        int[] pages = spreads.get(spread);
        int n = pages.length;
        boolean fitWidth = prefs.isPdfFitWidth();

        double availW = Math.max(50, vw - 2 * PADDING - (n > 1 ? GAP : 0));
        double availH = Math.max(50, vh - 2 * PADDING);
        double totalUnitWidth = 0;
        for (int p : pages) totalUnitWidth += source.pageSize(p).width();

        double scale;
        if (fitWidth) {
            scale = availW / totalUnitWidth;
        } else {
            scale = availW / totalUnitWidth;
            for (int p : pages) {
                scale = Math.min(scale, availH / source.pageSize(p).height());
            }
        }
        for (int i = 0; i < n; i++) {
            PageSize size = source.pageSize(pages[i]);
            views[i].setFitWidth(Math.floor(size.width() * scale));
            views[i].setFitHeight(Math.floor(size.height() * scale));
        }
        scroller.setFitToHeight(!fitWidth);
        spreadBox.setPadding(new Insets(PADDING));
    }

    // ------------------------------------------------------------------
    // Rendering (background thread + small cache)
    // ------------------------------------------------------------------

    private void requestRenders() {
        if (!ready || spreads.isEmpty()) return;
        int[] pages = spreads.get(spread);
        for (int i = 0; i < pages.length; i++) {
            render(pages[i], views[i], renderWidthFor(views[i]));
        }
        // Prefetch the neighbouring spreads so turning the page feels instant.
        prefetch(spread + 1);
        prefetch(spread - 1);
    }

    private void prefetch(int spreadIndex) {
        if (spreadIndex < 0 || spreadIndex >= spreads.size()) return;
        int[] pages = spreads.get(spreadIndex);
        double referenceWidth = views[0].getFitWidth();
        for (int p : pages) {
            // Approximate the width that page would get with the same scale as the current one.
            double width = referenceWidth * source.pageSize(p).width() / source.pageSize(spreads.get(spread)[0]).width();
            render(p, null, bucket(width * outputScale()));
        }
    }

    private int renderWidthFor(ImageView view) {
        return bucket(view.getFitWidth() * outputScale());
    }

    private double outputScale() {
        Stage stage = navigator.getStage();
        return stage == null ? 1.0 : Math.max(1.0, stage.getOutputScaleX());
    }

    private static int bucket(double widthPx) {
        int w = (int) Math.ceil(Math.max(64, widthPx) / WIDTH_BUCKET) * WIDTH_BUCKET;
        return Math.min(w, 4096);
    }

    /** Shows the page in {@code target} (may be null for a prefetch), rendering it if needed. */
    private void render(int page, ImageView target, int widthPx) {
        String key = page + "@" + widthPx;
        Image cached = cache.get(key);
        if (cached != null) {
            if (target != null) target.setImage(cached);
            return;
        }
        if (target != null && target.getImage() == null) {
            // Show any resolution we already have for this page while the right one renders.
            Image any = anyCachedFor(page);
            if (any != null) target.setImage(any);
        }
        if (pending.contains(key)) return;
        pending.add(key);
        PageSize size = source.pageSize(page);
        double scale = widthPx / size.width();
        long pixels = (long) (size.width() * scale) * (long) (size.height() * scale);
        if (pixels > MAX_RENDER_PIXELS) {
            scale *= Math.sqrt((double) MAX_RENDER_PIXELS / pixels);
        }
        double finalScale = scale;
        PageSource src = source; // the field is cleared on close; keep our own reference
        renderPool.submit(() -> {
            Image image;
            try {
                BufferedImage rendered = src.render(page, finalScale);
                image = toFxImage(rendered);
            } catch (Exception e) {
                System.err.println("Could not render page " + (page + 1) + ": " + e.getMessage());
                image = null;
            }
            Image result = image;
            Platform.runLater(() -> {
                pending.remove(key);
                if (closed || result == null) return;
                cache.put(key, result);
                // Apply only if that page is still on screen at that size.
                int[] pages = spreads.get(spread);
                for (int i = 0; i < pages.length; i++) {
                    if (pages[i] == page && renderWidthFor(views[i]) == widthPx) {
                        views[i].setImage(result);
                    }
                }
            });
        });
    }

    private Image anyCachedFor(int page) {
        String prefix = page + "@";
        for (Map.Entry<String, Image> e : cache.entrySet()) {
            if (e.getKey().startsWith(prefix)) return e.getValue();
        }
        return null;
    }

    private static Image toFxImage(BufferedImage rendered) {
        int w = rendered.getWidth();
        int h = rendered.getHeight();
        int[] pixels = rendered.getRGB(0, 0, w, h, null, 0, w);
        WritableImage image = new WritableImage(w, h);
        image.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
        return image;
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    private void next() {
        if (!ready) return;
        if (spread < spreads.size() - 1) {
            showSpread(spread + 1, false);
        } else {
            showEndOfBook();
        }
    }

    private void previous() {
        if (!ready) return;
        if (spread > 0) showSpread(spread - 1, false);
    }

    /** The page drawn on the right: the next one when reading left-to-right, else the previous. */
    private void visualRight() {
        if (prefs.isPdfRightToLeft()) previous(); else next();
    }

    private void visualLeft() {
        if (prefs.isPdfRightToLeft()) next(); else previous();
    }

    /** In fit-width mode a page can be taller than the window: scroll first, then turn. */
    private void nextOrScroll() {
        if (!ready) return;
        if (canScroll() && scroller.getVvalue() < 0.999) {
            scrollBy(0.85);
        } else {
            next();
        }
    }

    private void previousOrScroll() {
        if (!ready) return;
        if (canScroll() && scroller.getVvalue() > 0.001) {
            scrollBy(-0.85);
        } else if (spread > 0) {
            showSpread(spread - 1, true);
        }
    }

    private boolean canScroll() {
        Node content = scroller.getContent();
        return prefs.isPdfFitWidth() && content != null
                && content.getBoundsInLocal().getHeight() > scroller.getViewportBounds().getHeight() + 1;
    }

    private void scrollBy(double viewportFraction) {
        double contentHeight = scroller.getContent().getBoundsInLocal().getHeight();
        double viewportHeight = scroller.getViewportBounds().getHeight();
        double scrollable = Math.max(1, contentHeight - viewportHeight);
        double delta = viewportFraction * viewportHeight / scrollable;
        scroller.setVvalue(Math.max(0, Math.min(1, scroller.getVvalue() + delta)));
    }

    private void goToPage(int page) {
        if (!ready) return;
        showSpread(PageSpreads.spreadOf(spreads, page), false);
    }

    /** Reached by trying to turn the page on the last spread: the book is truly finished. */
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

    // ------------------------------------------------------------------
    // State, progress and saving
    // ------------------------------------------------------------------

    private void updateState() {
        if (closed || !ready || spreads.isEmpty()) return;
        int first = PageSpreads.firstPage(spreads, spread);
        int last = PageSpreads.lastPage(spreads, spread);
        int total = source.pageCount();
        pageLabel.setText((first == last ? "Page " + (first + 1) : "Pages " + (first + 1) + "–" + (last + 1))
                + " of " + total);
        double progress = (last + 1.0) / total;
        progressBar.setProgress(progress);
        progressLabel.setText(String.format(Locale.ROOT, "%.1f %%", progress * 100.0));
        chapterLabel.setText(chapterTitle(first));
        if (!endOfBookShown) hintLabel.setText(HINT);
        if (tocPanel.isVisible()) tocList.refresh();
        updateBookmarkButton();

        book.setPosition(first, 0.0);
        book.setReadingPercentage(progress * 100.0);
        book.setLastReadAt(System.currentTimeMillis());
        scheduleSave();
    }

    /** Title of the last outline entry that starts at or before the page, or empty. */
    private String chapterTitle(int page) {
        OutlineEntry best = null;
        for (OutlineEntry e : source.outline()) {
            if (e.pageIndex() <= page && (best == null || e.pageIndex() >= best.pageIndex())) best = e;
        }
        return best == null ? "" : best.title();
    }

    private int outlineIndexForPage(int page) {
        int best = -1;
        List<OutlineEntry> entries = source.outline();
        for (int i = 0; i < entries.size(); i++) {
            OutlineEntry e = entries.get(i);
            if (e.pageIndex() <= page && (best < 0 || e.pageIndex() >= entries.get(best).pageIndex())) best = i;
        }
        return best;
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

        scroller.addEventFilter(ScrollEvent.SCROLL, ev -> {
            if (!ready) { ev.consume(); return; }
            if (ev.isInertia()) { ev.consume(); return; }
            long now = System.currentTimeMillis();
            boolean scrollable = canScroll();
            double v = scroller.getVvalue();
            boolean atEdge = !scrollable || (ev.getDeltaY() < 0 ? v >= 0.999 : v <= 0.001);
            if (!atEdge) {
                lastScrollAt = now; // let the ScrollPane scroll the page
                return;
            }
            ev.consume();
            if (scrollable && now - lastScrollAt < 300) return; // just reached the edge: pause before turning
            wheelAccumulated += ev.getDeltaY();
            if (Math.abs(wheelAccumulated) >= 30 && now - lastWheelFlip > 160) {
                if (wheelAccumulated < 0) next(); else if (spread > 0) showSpread(spread - 1, scrollable);
                wheelAccumulated = 0;
                lastWheelFlip = now;
            }
        });
        // Click on the outer 15% of the viewer turns the page; the side follows the reading direction.
        viewerContainer.addEventFilter(MouseEvent.MOUSE_CLICKED, ev -> {
            if (!ready || ev.getButton() != MouseButton.PRIMARY || !ev.isStillSincePress()) return;
            double w = viewerContainer.getWidth();
            if (ev.getX() < w * 0.15) { visualLeft(); ev.consume(); }
            else if (ev.getX() > w * 0.85) { visualRight(); ev.consume(); }
        });
        viewerContainer.addEventFilter(MouseEvent.MOUSE_PRESSED, ev -> {
            if (ev.getButton() == MouseButton.BACK) { previous(); ev.consume(); }
            else if (ev.getButton() == MouseButton.FORWARD) { next(); ev.consume(); }
        });
    }

    private void onKey(KeyEvent ev) {
        if (closed) return;
        if (settingsPopup != null && settingsPopup.isShowing()) return;
        Node focused = navigator.getScene().getFocusOwner();
        boolean focusInToc = tocPanel.isVisible() && focused != null && isDescendant(focused, tocPanel);
        if (focusInToc) {
            if (ev.getCode() == KeyCode.ESCAPE || ev.getCode() == KeyCode.T) {
                showToc(false);
                ev.consume();
            } else if (ev.getCode() == KeyCode.TAB) {
                sidePanel.switchToOther(); // Contents <-> Bookmarks
                ev.consume();
            }
            return; // arrows, Enter, N and Delete are handled by the lists themselves
        }
        boolean ctrl = ev.isControlDown() || ev.isShortcutDown();
        switch (ev.getCode()) {
            case RIGHT -> { visualRight(); ev.consume(); }
            case LEFT -> { visualLeft(); ev.consume(); }
            case PAGE_DOWN, DOWN -> { nextOrScroll(); ev.consume(); }
            case PAGE_UP, UP -> { previousOrScroll(); ev.consume(); }
            case SPACE -> { if (ev.isShiftDown()) previousOrScroll(); else nextOrScroll(); ev.consume(); }
            case HOME -> { showSpread(0, false); ev.consume(); }
            case END -> { showSpread(spreads.size() - 1, false); ev.consume(); }
            case ESCAPE -> {
                if (tocPanel.isVisible()) showToc(false);
                else if (navigator.getStage().isFullScreen()) navigator.getStage().setFullScreen(false);
                else onBack();
                ev.consume();
            }
            case F11 -> { toggleFullScreen(); ev.consume(); }
            case T -> { if (!ctrl) { toggleToc(); ev.consume(); } }
            case B -> { if (!ctrl) { toggleBookmark(); ev.consume(); } }
            case R -> { if (!ctrl) { changeDirection(prefs.isPdfRightToLeft() ? Preferences.PDF_DIRECTION_LTR : Preferences.PDF_DIRECTION_RTL); ev.consume(); } }
            case W -> { if (!ctrl) { changeFit(prefs.isPdfFitWidth() ? Preferences.PDF_FIT_PAGE : Preferences.PDF_FIT_WIDTH); ev.consume(); } }
            case DIGIT1, NUMPAD1 -> { if (!ctrl) { changeLayout(Preferences.PDF_LAYOUT_SINGLE); ev.consume(); } }
            case DIGIT2, NUMPAD2 -> { if (!ctrl) { changeLayout(Preferences.PDF_LAYOUT_DOUBLE); ev.consume(); } }
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
            ListCell<OutlineEntry> cell = new ListCell<>() {
                @Override
                protected void updateItem(OutlineEntry item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("current");
                    if (empty || item == null) {
                        setText(null);
                        return;
                    }
                    setText(item.title());
                    setPadding(new Insets(6, 8, 6, 8 + 16.0 * Math.min(4, item.level())));
                    if (ready && getIndex() == outlineIndexForPage(PageSpreads.firstPage(spreads, spread))) {
                        getStyleClass().add("current");
                    }
                }
            };
            cell.setWrapText(true);
            cell.setPrefWidth(0);
            return cell;
        });
        tocList.setOnMouseClicked(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY) {
                OutlineEntry selected = tocList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    goToPage(selected.pageIndex());
                    showToc(false);
                }
            }
        });
        tocList.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER) {
                OutlineEntry selected = tocList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    goToPage(selected.pageIndex());
                    showToc(false);
                }
                ev.consume();
            }
        });
    }

    private void toggleToc() {
        showToc(!sidePanel.isVisible());
    }

    /** Opens the side panel on the contents (or on the bookmarks when there is no outline). */
    private void showToc(boolean show) {
        if (!show) {
            sidePanel.hide();
            return;
        }
        sidePanel.show(ReaderSidePanel.Tab.CONTENTS);
        if (sidePanel.tab() == ReaderSidePanel.Tab.CONTENTS) {
            tocList.refresh();
            int current = ready ? outlineIndexForPage(PageSpreads.firstPage(spreads, spread)) : -1;
            if (current >= 0) {
                tocList.getSelectionModel().select(current);
                tocList.scrollTo(Math.max(0, current - 3));
            }
        }
    }

    // ------------------------------------------------------------------
    // Bookmarks (one per page: the first page of the spread on screen)
    // ------------------------------------------------------------------

    private void toggleBookmark() {
        if (!ready || spreads.isEmpty()) return;
        int page = PageSpreads.firstPage(spreads, spread);
        Bookmark existing = book.findBookmark(page, 0.0, 0.0);
        if (existing != null) {
            book.removeBookmark(existing);
            flashHint("Bookmark removed");
        } else {
            book.addBookmark(new Bookmark(page, 0.0, ""));
            flashHint("Bookmark added  ·  T shows your bookmarks");
        }
        bookmarks.refresh();
        updateBookmarkButton();
        scheduleSave();
    }

    private void updateBookmarkButton() {
        if (!ready || spreads.isEmpty()) return;
        boolean marked = false;
        for (int page : spreads.get(spread)) {
            if (book.findBookmark(page, 0.0, 0.0) != null) marked = true;
        }
        bookmarkButton.setText(marked ? "★" : "☆");
    }

    private String describeBookmark(Bookmark b) {
        String where = "Page " + (b.getChapter() + 1);
        String title = ready ? chapterTitle(b.getChapter()) : "";
        return title.isEmpty() ? where : where + "  ·  " + title;
    }

    private void goToBookmark(Bookmark b) {
        showToc(false);
        goToPage(b.getChapter());
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
    // Settings: layout, direction, fit and theme
    // ------------------------------------------------------------------

    private void changeLayout(String layout) {
        if (prefs.getPdfLayout().equalsIgnoreCase(layout)) return;
        prefs.setPdfLayout(layout);
        if (ready) {
            int page = PageSpreads.firstPage(spreads, spread);
            rebuildSpreads();
            showSpread(PageSpreads.spreadOf(spreads, page), false);
        }
        scheduleSave();
    }

    private void changeDirection(String direction) {
        if (prefs.getPdfDirection().equalsIgnoreCase(direction)) return;
        prefs.setPdfDirection(direction);
        if (ready) showSpread(spread, false);
        scheduleSave();
    }

    private void changeFit(String fit) {
        if (prefs.getPdfFit().equalsIgnoreCase(fit)) return;
        prefs.setPdfFit(fit);
        if (ready) showSpread(spread, false);
        scheduleSave();
    }

    private void changeTheme(String theme) {
        if (prefs.getTheme().equalsIgnoreCase(theme)) return;
        prefs.setTheme(theme);
        applyUiTheme();
        scheduleSave();
    }

    private void applyUiTheme() {
        String css = switch (prefs.getTheme()) {
            case Preferences.THEME_SEPIA -> "theme-sepia";
            case Preferences.THEME_DARK -> "theme-dark";
            default -> "theme-light";
        };
        root.getStyleClass().removeAll("theme-light", "theme-sepia", "theme-dark");
        root.getStyleClass().add(css);
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

        box.getChildren().add(section("LAYOUT", UiControls.segmented(prefs.getPdfLayout(), this::changeLayout,
                new String[][]{{Preferences.PDF_LAYOUT_SINGLE, "Single page"}, {Preferences.PDF_LAYOUT_DOUBLE, "Double page"}})));
        box.getChildren().add(section("READING DIRECTION", UiControls.segmented(prefs.getPdfDirection(), this::changeDirection,
                new String[][]{{Preferences.PDF_DIRECTION_LTR, "Left to right"}, {Preferences.PDF_DIRECTION_RTL, "Right to left"}})));
        box.getChildren().add(section("FIT", UiControls.segmented(prefs.getPdfFit(), this::changeFit,
                new String[][]{{Preferences.PDF_FIT_PAGE, "Whole page"}, {Preferences.PDF_FIT_WIDTH, "Width"}})));
        box.getChildren().add(section("THEME", UiControls.segmented(prefs.getTheme(), this::changeTheme,
                new String[][]{{Preferences.THEME_LIGHT, "Light"}, {Preferences.THEME_SEPIA, "Sepia"}, {Preferences.THEME_DARK, "Dark"}})));

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.getContent().add(box);
        popup.setOnHidden(e -> scroller.requestFocus());
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
    private void onToggleFullScreen() {
        toggleFullScreen();
    }

    private void toggleFullScreen() {
        Stage stage = navigator.getStage();
        stage.setFullScreen(!stage.isFullScreen());
        scroller.requestFocus();
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
        if (renderPool != null) renderPool.shutdownNow();
        cache.clear();
        for (ImageView v : views) v.setImage(null);
        Stage stage = navigator.getStage();
        if (stage.isFullScreen()) stage.setFullScreen(false);
        if (!wasMaximized) stage.setMaximized(false);
        PageSource open = source;
        source = null;
        if (open != null) {
            Thread closer = new Thread(open::close, "pdf-close");
            closer.setDaemon(true);
            closer.start();
        }
    }
}
