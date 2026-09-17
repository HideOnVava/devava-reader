# Devava Reader

A minimal, distraction-free desktop EPUB reader for book collections — built by
**devava XP Studios** with Java 21 and JavaFX 21.

Organize long series (light novels, sagas) into collections, keep your own volume order,
track reading progress per volume, and read in a clean two-page "open book" layout.

## Features

- **Collections** of volumes with custom ordering, progress bars and read/unread status.
- **Two-page (or single-page) reading view** with exact pagination: no lost or clipped pages.
- **Table of contents** side panel (EPUB 3 `nav` and EPUB 2 NCX).
- **Footnote and internal links** open in place, with a back button to return.
- **Reader settings**: font size, serif/sans typeface, light/sepia/dark themes, one or two columns.
- **Progress weighted by chapter size**, so an illustration page does not count like a long chapter.
- **"Continue reading"** card on the main screen.
- Drag & drop `.epub` files onto a collection; natural sort on import (Volume 2 before Volume 10).
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

`packaging/build-windows.ps1` compiles the project and uses `jpackage` (bundled with the JDK)
to produce a self-contained app image — the user does not need Java installed:

```powershell
.\packaging\build-windows.ps1
```

The result is `dist\Devava Reader\Devava Reader.exe`. Add `-Shortcut` to also create a desktop
shortcut, or `-Destination "$env:LOCALAPPDATA\Programs"` to install it per user.
`-Type exe` / `-Type msi` build an installer instead (requires the WiX Toolset).

## Where the data lives

The library file `library.json` is created automatically on first launch in
`<Documents>\Devava Reader\`, where `<Documents>` is the user's real Documents folder
(OneDrive redirection is honored). No manual setup is needed.

- Override the location with `-Dreader.library=path\to\library.json`.
- Writes are atomic; a corrupt file is preserved as `library.corrupt-<date>.json`.
- A library created by an earlier version of the app (Spanish field names under
  `Documents\MiLector\biblioteca.json`) is imported automatically the first time, so no reading
  progress is lost. The old file is left untouched.

While a book is open, its EPUB is extracted to `%TEMP%\DevavaReader\<id>` and removed on exit.

## Keyboard shortcuts (reader)

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

In a collection: `Enter` or double-click opens the reader, `F2` renames, `Del` removes the
volume, `Ctrl+↑` / `Ctrl+↓` reorder, and `.epub` files can be dropped onto the list.

## Project layout

```
com.devavaxp.reader
├── ReaderApp / Launcher        startup, single window and window preferences
├── Navigator                   screen switching on a single Scene
├── CollectionsController       library: collections and "continue reading"
├── VolumesController           volumes of a collection: import, order, mark, read
├── ReaderController            reader: chapters, keyboard/mouse, contents, settings, progress
├── Dialogs                     dialogs styled like the app
├── bridge/JsBridge             object exposed to reader.js (link clicks, events)
├── data/DataManager            JSON persistence (Gson), atomic and fault-tolerant, legacy import
├── data/TextUtils              natural order, titles, plurals
├── epub/EpubExtractor          safe ZIP extraction into the temp cache
├── epub/EpubParser             container.xml → OPF (spine, manifest) → nav / NCX (contents)
├── epub/EpubBook               reading order, table of contents and size-weighted progress
└── model/                      BookCollection, Book, Preferences
resources/com/devavaxp/reader
├── *.fxml, styles.css          views and stylesheet
└── reader.js                   pagination engine (injected into every chapter)
packaging/
├── build-windows.ps1           jpackage build script
└── icon.ico                    application icon
```

## How pagination works

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

Copyright © devava XP Studios. All rights reserved unless a license file states otherwise.
