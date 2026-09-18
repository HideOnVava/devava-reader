# Devava Reader

[![Download the latest release](https://img.shields.io/github/v/release/HideOnVava/devava-reader?label=download&color=5b5bd6)](https://github.com/HideOnVava/devava-reader/releases/latest)
[![CI](https://github.com/HideOnVava/devava-reader/actions/workflows/ci.yml/badge.svg)](https://github.com/HideOnVava/devava-reader/actions/workflows/ci.yml)
[![Platform: Windows 10/11](https://img.shields.io/badge/platform-Windows%2010%2F11-0078D4)](#download-windows)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A minimal, distraction-free desktop reader for **EPUB novels** and **PDF manga**, organized in
collections — built by **devava XP Studios**.

Keep long series (light novels, sagas, manga) in collections, in the order *you* want, and
pick up every volume exactly where you left it. No accounts, no cloud, no Java to install:
one download and it runs.

| Library | Collection |
| --- | --- |
| ![Library screen](docs/screenshots/library.png) | ![A collection with its volumes](docs/screenshots/collection.png) |

| EPUB reader | PDF / manga reader |
| --- | --- |
| ![EPUB reader, two columns](docs/screenshots/reader-epub.png) | ![PDF reader, double page, right to left](docs/screenshots/reader-pdf.png) |

## Download (Windows)

**[⬇ Download the latest version](https://github.com/HideOnVava/devava-reader/releases/latest)**
— Windows 10 or 11, 64-bit. Nothing else is needed.

1. On that page, under **Assets**, click **`Devava-Reader-<version>-windows-x64-setup.exe`**.
2. Open the downloaded file. If Windows shows *"Windows protected your PC"*, click
   **More info → Run anyway** (see [why](#why-does-windows-show-a-warning) below).
3. Keep the suggested folder (or choose another) and finish. Devava Reader appears in the
   Start menu and, unless you untick the box, on the desktop. No administrator rights are needed.

Prefer not to install anything? Download **`Devava-Reader-<version>-windows-x64-portable.zip`**
instead, unzip it anywhere (a USB stick works) and open `Devava Reader.exe`.

### First steps

1. **Create a collection** — type a name (for example the series) and click *Create collection*.
2. **Add books** — open the collection and click *＋ Add books*, or drag `.epub` / `.pdf` files
   onto the list. Volumes are sorted naturally on import (*Volume 2* before *Volume 10*) and you
   can reorder them with the arrow buttons.
3. **Read** — double-click a volume. Turn pages with `→` / `←`, the mouse wheel or by clicking
   the page edges; press `Esc` to go back. Progress is saved as you read, and the *Continue
   reading* card on the first screen takes you straight back to the last book.

### Updating

Download the new `…-setup.exe` and run it: it replaces the previous version in place. Your
library (collections, volumes, reading progress and settings) is kept. With the portable
version, unzip the new one and delete the old folder.

### Uninstalling

*Settings → Apps → Installed apps → Devava Reader → Uninstall* (or just delete the folder of the
portable version). Your library file stays in `Documents\Devava Reader` in case you come back;
your book files are never moved, copied or modified by the app.

### Why does Windows show a warning?

The downloads are not code-signed — a signing certificate costs money every year and this is a
free project. SmartScreen therefore shows *"Windows protected your PC"* the first time you run
an unsigned program; *More info → Run anyway* is all it takes. Every release is built
automatically by GitHub Actions from the tagged source code, so the build log shows exactly what
went into the files, and each release lists their SHA-256 checksums.

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

## Keyboard shortcuts

### EPUB reader

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

### PDF / manga reader

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
saved with the library and apply to every PDF. Reaching the end of the last spread and turning
once more marks the volume as read.

### Library and collections

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

## Where the data lives

The library file `library.json` is created automatically on first launch in
`<Documents>\Devava Reader\`, where `<Documents>` is the user's real Documents folder
(OneDrive redirection is honored). No manual setup is needed.

- Override the location with `-Dreader.library=path\to\library.json` (when running from
  source) or with the environment variable `JAVA_TOOL_OPTIONS=-Dreader.library=…` (packaged app).
- Writes are atomic; a corrupt file is preserved as `library.corrupt-<date>.json`.
- A library created by an earlier version of the app (Spanish field names under
  `Documents\MiLector\biblioteca.json`) is imported automatically the first time, so no reading
  progress is lost. The old file is left untouched.
- Every volume records its `format` (`epub` or `pdf`, derived from the file extension), and
  every collection records its position and whether it is pinned. Files written before these
  fields existed load normally and get them filled in.

While a book is open, its EPUB is extracted to `%TEMP%\DevavaReader\<id>` and removed on exit.
PDFs are read in place; nothing is extracted.

---

## For developers

Java 21, JavaFX 21, Maven (wrapper included), Gson, Apache PDFBox. Unit tests with JUnit 5;
the compiler runs with `-Xlint:all` and the build is expected to stay warning-free.

### Running from source

Requirements: JDK 21 and an internet connection for the first Maven build.

```bash
./mvnw javafx:run
```

From IntelliJ IDEA: open the folder as a Maven project and run `com.devavaxp.reader.ReaderApp`
(or `Launcher`).

Tests:

```bash
./mvnw test
```

## Building the Windows application

This turns the source code into the same thing the [releases](https://github.com/HideOnVava/devava-reader/releases)
contain: a folder with `Devava Reader.exe` and a bundled Java runtime, that runs on any Windows
10/11 PC without Java installed. The steps below assume you have never done this before.
Only the installer (step 6) needs an extra tool; everything else is included.

### 1. Install a JDK 21 (once)

The JDK is the Java development kit: it contains the compiler and the two tools that build
the application (`jlink` and `jpackage`).

1. Go to <https://adoptium.net/temurin/releases/?version=21&os=windows&arch=x64&package=jdk>
   and download the **JDK** (not the JRE) **21** installer for Windows x64 (`.msi`).
2. Run it. In the *Custom Setup* page, enable **"Set JAVA_HOME variable"** — the build script
   uses that variable to find the JDK. Keep the rest as it is and finish.
3. Open a **new** PowerShell window (Start menu → type `powershell` → Enter) and check:

   ```powershell
   java -version
   ```

   The first line must say version **21** (for example `openjdk version "21.0.9"`).
   If it shows an older version or "not recognized", a different Java is first on your PATH;
   the script can be told where the JDK is with `-JdkHome "C:\Program Files\Eclipse Adoptium\jdk-21..."`.

### 2. Get the source code

Either of these:

- **Without Git**: on the repository page click the green **Code** button → **Download ZIP**,
  then extract the ZIP somewhere simple, for example `C:\Projects\devava-reader`.
- **With Git** (if you have it):

  ```powershell
  git clone https://github.com/HideOnVava/devava-reader.git C:\Projects\devava-reader
  ```

### 3. Open PowerShell in the project folder

In File Explorer, open the folder that contains `pom.xml` and `mvnw.cmd`, click in the
address bar, type `powershell` and press Enter. A PowerShell window opens already positioned
in that folder (the prompt shows its path).

### 4. Run the build script

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\build-windows.ps1
```

`-ExecutionPolicy Bypass` is needed because Windows blocks unsigned scripts by default; it only
applies to this one command. What happens next:

1. Maven compiles the project. **The first run downloads the build tool and the libraries
   (JavaFX, PDFBox, Gson — a few hundred MB) and takes several minutes**; later runs are fast.
2. `jlink` assembles a trimmed Java runtime with only the modules the app needs.
3. `jpackage` combines runtime, application and icon into the app image.

When it finishes it prints `Built: …\dist\Devava Reader`. That folder is the application:
double-click `dist\Devava Reader\Devava Reader.exe` to run it, or copy the whole folder
anywhere you like (that folder, zipped, is exactly the portable download of a release).

### 5. Optional: install it for yourself, with a desktop shortcut

```powershell
powershell -ExecutionPolicy Bypass -File .\packaging\build-windows.ps1 -Destination "$env:LOCALAPPDATA\Programs" -Shortcut
```

This puts the app in `%LOCALAPPDATA%\Programs\Devava Reader` (your user's programs folder, no
administrator rights) and creates *Devava Reader* on the desktop. Running the same command
again after changing the code replaces it.

### 6. Optional: build the installer (`-setup.exe`)

The installer is the app image wrapped by the **WiX Toolset 3.x**, a free Microsoft-backed
tool that `jpackage` uses to create Windows installers.

1. Download `wix314.exe` from <https://github.com/wixtoolset/wix3/releases> and install it
   (it may ask for the .NET Framework 3.5 — accept). The installer sets the `WIX` environment
   variable that the script looks for.
2. Open a new PowerShell window in the project folder and run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\packaging\build-windows.ps1 -Type exe
   ```

The result is `dist\Devava Reader-<version>.exe`: a per-user installer with a folder chooser,
Start menu and desktop shortcuts, and an *Uninstall* entry in Windows Settings. Installing a
newer version over an older one upgrades it in place.

### If something goes wrong

| Message | What it means / what to do |
| --- | --- |
| `jlink/jpackage/jmods not found` | `JAVA_HOME` does not point to a full JDK 21. Re-run the JDK installer with *Set JAVA_HOME* enabled, open a new PowerShell window, or pass `-JdkHome "C:\path\to\jdk-21"`. |
| `running scripts is disabled on this system` | The command was run without `-ExecutionPolicy Bypass`; use the exact command from step 4. |
| `Maven build failed` with network errors | The first build needs internet access to download the libraries. Check the connection (or proxy) and run the script again. |
| `The WiX Toolset 3.x is required` | Only for `-Type exe` / `msi`: install WiX as in step 6 and open a new PowerShell window. |
| *Windows protected your PC* when starting the built app | Normal for unsigned programs: *More info → Run anyway*. |

### How the build works

`build-windows.ps1` compiles with the Maven wrapper, copies the dependencies, builds a runtime
image with `jlink` (the JDK modules listed in the script plus the JavaFX modules) and calls
`jpackage` with that runtime and the application jars on the class path; the version number
is read from `pom.xml`. Inside the image the application and its libraries run from the class
path while JavaFX lives in the runtime image as proper modules: PDFBox ships as automatic
modules, which `jlink` cannot link. When run from source (`javafx:run`, IDE) everything is on
the module path instead, which is why `module-info.java` also requires
`org.apache.commons.logging` — PDFBox needs it but, being automatic, cannot declare it.
Installers are built from that same image with `--app-image`, so the ZIP and the installer of a
release contain identical bits.

## Releasing a new version

Releases are built and published by GitHub Actions ([`.github/workflows/release.yml`](.github/workflows/release.yml))
so that every download comes from a clean, reproducible build of a tagged commit — nothing is
uploaded from a developer's PC. To publish, for example, version 1.2.0:

1. Set `<version>1.2.0</version>` in `pom.xml`.
2. Add a `## [1.2.0] - <date>` section to [`CHANGELOG.md`](CHANGELOG.md) describing the
   changes (it becomes the "What's new" text of the release) and the link reference at the
   bottom of the file.
3. Commit and push, then create and push the tag:

   ```bash
   git tag v1.2.0
   git push origin v1.2.0
   ```

4. The **Release** workflow starts automatically: it checks that the tag matches `pom.xml`,
   runs the tests, builds the installer and the portable ZIP with `packaging/release.ps1`,
   computes the SHA-256 checksums and publishes the release with notes generated from the
   changelog. It appears under **Releases** after a few minutes.

To rehearse without publishing, open **Actions → Release → Run workflow**: the same build runs
and the files are attached to the workflow run as an artifact instead of a release. The script
can also be run locally (`.\packaging\release.ps1`, or with `-SkipInstaller` when WiX is not
installed); its output in `dist\release` is what the workflow uploads.

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
├── build-windows.ps1           jlink + jpackage build script (app image or installer)
├── release.ps1                 release files: installer, portable ZIP, checksums, notes
├── release-notes.md            template of the release text
└── icon.ico                    application icon
.github/workflows/
├── ci.yml                      build and tests on every push / pull request
└── release.yml                 build and publish a release from a version tag
docs/screenshots/               images used by this README
```

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

## License

Copyright © 2026 devava XP Studios. Released under the [MIT License](LICENSE).

The packaged Windows build bundles third-party components under their own licenses:
OpenJFX (GPL v2 with the Classpath Exception), Gson (Apache License 2.0) and Apache PDFBox
with FontBox and Commons Logging (Apache License 2.0).
