# Devava Reader

A minimal, distraction-free desktop reader for EPUB books and PDF manga, organized in
collections — built by **devava XP Studios** with Java 21 and JavaFX 21.

Organize long series (light novels, sagas, manga) into collections, keep your own volume
order, track reading progress per volume, and read in a clean two-page "open book" layout.

## Features

- **Collections** of volumes with custom ordering, progress bars and read/unread status. A
  collection can mix `.epub` and `.pdf` volumes.
- **Library search and filters**: find collections by name, filter them by the format of their
  volumes (all / EPUB only / PDF only), pin favourites so they always stay on top, and reorder
  collections manually.
- **EPUB reader** with a two-page (or single-page) view and exact pagination: no lost or
  clipped pages. Table of contents side panel (EPUB 3 `nav` and EPUB 2 NCX), footnote and
  internal links that open in place with a back button, font size, serif/sans typeface, one or
  two columns, and progress weighted by chapter size.
- **PDF / manga reader**: pages rendered as images (Apache PDFBox), single or double-page
  spreads with the cover alone and wide pages shown on their own, left-to-right or
  right-to-left reading direction, fit to the whole page or to the width (with scrolling), the
  PDF outline as a table of contents, and progress by page.
- **Light / sepia / dark themes** shared by both readers.
- **"Continue reading"** card on the main screen.
- Drag & drop `.epub` / `.pdf` files onto a collection; natural sort on import (Volume 2 before
  Volume 10).
- Everything is stored in a single JSON file that the app creates on first launch.

## Running from source

Requirements: JDK 21 and an internet connection for the first Maven build.

```bash
./mvnw javafx:run
```

From IntelliJ IDEA: run `com.devavaxp.reader.ReaderApp` (or `Launcher`).

Tests:

```bash
./mvnw test
```

## Building the Windows application

`packaging/build-windows.ps1` compiles the project, builds a trimmed Java runtime with `jlink`
(the JDK modules the app needs plus JavaFX) and uses `jpackage` (both bundled with the JDK) to
produce a self-contained app image — the user does not need Java installed:

```powershell
.\packaging\build-windows.ps1
```

The result is `dist\Devava Reader\Devava Reader.exe`. Add `-Shortcut` to also create a desktop
shortcut, or `-Destination "$env:LOCALAPPDATA\Programs"` to install it per user.
`-Type exe` / `-Type msi` build an installer instead (requires the WiX Toolset).

Inside the image the application and its libraries run from the class path while JavaFX
lives in the runtime image as proper modules: PDFBox ships as automatic modules, which `jlink`
cannot link. When run from source (`javafx:run`, IDE) everything is on the module path instead,
which is why `module-info.java` also requires `org.apache.commons.logging` — PDFBox needs it
but, being automatic, cannot declare it.

## Where the data lives

The library file `library.json` is created automatically on first launch in
`<Documents>\Devava Reader\`, where `<Documents>` is the user's real Documents folder
(OneDrive redirection is honored). No manual setup is needed.

- Override the location with `-Dreader.library=path\to\library.json`.
- Writes are atomic; a corrupt file is preserved as `library.corrupt-<date>.json`.
- A library created by an earlier version of the app (Spanish field names under
  `Documents\MiLector\biblioteca.json`) is imported automatically the first time, so no reading
  progress is lost. The old file is left untouched.
- Every volume records its `format` (`epub` or `pdf`, derived from the file extension), and
  every collection records its position and whether it is pinned. Files written before these
  fields existed load normally and get them filled in.

While a book is open, its EPUB is extracted to `%TEMP%\DevavaReader\<id>` and removed on exit.
PDFs are read in place; nothing is extracted.

## Keyboard shortcuts (EPUB reader)

| Key | Action |
| --- | --- |
| `→` `Space` `PgDn` `↓` | Next page (also mouse wheel and clicking the right edge) |
| `←` `Shift+Space` `PgUp` `↑` | Previous page (mouse wheel, clicking the left edge) |
| `Ctrl+→` / `Ctrl+←` | Next / previous chapter |
| `Home` / `End` | Start / end of the chapter (`Ctrl` = of the book) |
| `T` | Table of contents |
| `Backspace` / `Alt+←` | Go back to where you were before following a link |
| `Ctrl` `+` / `Ctrl` `-` | Font size |
| `1` / `2` | One or two columns |
| `F11` | Full screen |
| `Esc` | Close contents / leave full screen / back to the collection |

The **Aa** button opens the settings popup: font size, typeface, theme and columns.
They are saved with the library.

## Keyboard shortcuts (PDF / manga reader)

| Key | Action |
| --- | --- |
| `→` / `←` | Turn to the page drawn on the right / left (so they follow the reading direction); also clicking the right / left edge |
| `Space` `PgDn` `↓` | Next spread — in fit-to-width mode, scroll down first |
| `Shift+Space` `PgUp` `↑` | Previous spread — in fit-to-width mode, scroll up first |
| Mouse wheel | Next / previous spread (scrolls the page first when it is taller than the window) |
| `Home` / `End` | First / last spread |
| `1` / `2` | Single page / double page (the cover and wide pages always stand alone) |
| `R` | Toggle reading direction (left-to-right / right-to-left, for manga) |
| `W` | Toggle fit: whole page / width |
| `T` | Table of contents (the PDF outline, when the file has one) |
| `F11` | Full screen |
| `Esc` | Close contents / leave full screen / back to the collection |

The **⚙** button opens the settings popup: layout, reading direction, fit and theme. They are
saved with the library and apply to every PDF. Pages are rendered in a background thread at
the size they are shown (HiDPI aware), neighbouring spreads are prefetched and a small cache
keeps recent pages, so turning pages is instant even on large scans. Reaching the end of the
last spread and turning once more marks the volume as read.

In the library: type in the search box to filter collections by name (`Esc` clears it, `↓`
jumps to the list), the **All / EPUB / PDF** buttons filter by the format of the volumes a
collection holds (a collection only counts as "EPUB" or "PDF" when every volume in it has
that format; empty or mixed collections only appear under *All*), right-click a collection to
**pin** or unpin it (pinned collections are always listed first), and `Ctrl+↑` / `Ctrl+↓` or
the arrow buttons move a collection within its group. Moving is only available while the full,
unfiltered list is shown, so that a position always means the real position.

In a collection: `Enter` or double-click opens the reader (the EPUB or the PDF one, depending
on the volume), `F2` renames, `Del` removes the volume, `Ctrl+↑` / `Ctrl+↓` reorder, and
`.epub` / `.pdf` files can be dropped onto the list. PDF volumes show a small **PDF** badge.

## Project layout

```
com.devavaxp.reader
├── ReaderApp / Launcher        startup, single window and window preferences
├── Navigator                   screen switching on a single Scene; picks the reader by format
├── CollectionsController       library: search, filters, pinning, ordering, "continue reading"
├── VolumesController           volumes of a collection: import (.epub/.pdf), order, mark, read
├── ReaderController            EPUB reader: chapters, keyboard/mouse, contents, settings, progress
├── PdfReaderController         fixed-page reader: spreads, direction, fit, background rendering
├── UiControls                  small controls built in code (segmented button rows)
├── Dialogs                     dialogs styled like the app
├── bridge/JsBridge             object exposed to reader.js (link clicks, events)
├── data/DataManager            JSON persistence (Gson), atomic and fault-tolerant, legacy import,
│                               collection order/pinning and format queries
├── data/TextUtils              natural order, titles, plurals
├── epub/EpubExtractor          safe ZIP extraction into the temp cache
├── epub/EpubParser             container.xml → OPF (spine, manifest) → nav / NCX (contents)
├── epub/EpubBook               reading order, table of contents and size-weighted progress
├── pdf/PageSource              what the fixed-page reader needs from a book (pages, sizes,
│                               rendering, outline) — the seam for other formats, e.g. CBZ
├── pdf/PdfPageSource           PageSource backed by Apache PDFBox
├── pdf/PageSpreads             grouping of pages into single / double-page spreads
└── model/                      BookCollection, Book, Preferences
resources/com/devavaxp/reader
├── *.fxml, styles.css          views and stylesheet
└── reader.js                   pagination engine (injected into every EPUB chapter)
packaging/
├── build-windows.ps1           jlink + jpackage build script
└── icon.ico                    application icon
```

## How the PDF reader works

`PdfReaderController` only talks to a `PageSource`: page count, page sizes (with the page
rotation applied), an image for a page at a given scale, and an outline. `PdfPageSource`
implements it with PDFBox, reading the file on demand and caching decoded streams in
temporary files rather than in the heap, which keeps large scanned volumes cheap to open.

Pages are grouped into spreads by `PageSpreads`: one page each in single layout; in double
layout the cover stands alone, the rest are paired (2–3, 4–5, …) the way printed comics are
bound, and a landscape page — a two-page spread scanned as one image — stands alone with the
pairing carrying on after it. The reading direction only decides on which side of the spread
each page is drawn, and `→` / `←` always turn towards the side they point at.

Each visible page is sized from the viewport (one shared scale per spread, so the two pages
line up) and rendered on a single background thread at that pixel width, multiplied by the
screen scale on HiDPI displays; render widths are rounded up to 64-pixel buckets so small
window changes reuse the cached image. Results are applied only if that page is still on
screen at that size, the neighbouring spreads are prefetched, and an LRU cache keeps the
last eight images. The position is stored as `page:0`, so the same `savedPosition` field
serves both readers.

## How EPUB pagination works

Each spine file is loaded into the `WebView` and `reader.js` turns the `<body>` into a
multi-column container exactly as tall as the window. A "view" is two columns (or one).
The key points, verified empirically against the WebKit build shipped with JavaFX:

- The effective width is rounded to a multiple of the column count, so the column pitch is
  an integer and every view starts on an exact pixel (`view × width`).
- Views are moved with `transform: translateX(...)` on the `<body>`, but **`scrollWidth` is
  never measured with the transform applied**: in this WebKit the transform itself shrinks
  `scrollWidth`, which used to cause premature chapter jumps.
- The column count is `round((scrollWidth + padding) / pitch)`, tolerant to any sub-pixel
  deviation, capped by the last column that holds real content (avoids blank pages caused by
  trailing margins).
- `column-count: 1` does not create a multi-column context in WebKit; an explicit
  `column-width` is used instead.
- Images that do not fit in what is left of a column (which WebKit would "slice") are shrunk
  to the available space.
- On window resize, font-size or column changes the text being read stays in place
  (anchored through `caretRangeFromPoint`).

The position is stored as `chapter:fraction`, and the overall percentage is weighted by the
size of each chapter.

## License

Copyright © 2026 devava XP Studios. Released under the [MIT License](LICENSE).

The packaged Windows build bundles third-party components under their own licenses:
OpenJFX (GPL v2 with the Classpath Exception), Gson (Apache License 2.0) and Apache PDFBox
with FontBox and Commons Logging (Apache License 2.0).
