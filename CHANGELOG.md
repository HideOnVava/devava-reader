# Changelog

All notable changes to Devava Reader are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow
[Semantic Versioning](https://semver.org/). Each release on GitHub takes its notes from
the matching section of this file.

## [1.5.0] - 2026-09-20

### Added
- **Import a folder as a collection**: the *Import folder…* button in the library, or a
  folder dropped anywhere on it, creates a collection named after the folder with every
  `.epub` and `.pdf` inside it as volumes, in shelf order (the files of the folder first,
  in natural order, then each subfolder). Several folders can be dropped at once. If a
  collection with that name already exists, only the volumes that are new are added, so
  the same folder can be dropped again after new volumes arrive.
- Folders can also be dropped on a collection: their books are added to it.

### Changed
- The list highlights itself while files or folders are dragged over it.

## [1.4.0] - 2026-09-19

### Added
- **Search inside the book** (EPUB): `Ctrl+F` (`⌘F` on macOS) opens the side panel on a
  new *Search* tab. Results list the chapter and the passage with the match highlighted;
  `Enter` or a click jumps to that exact occurrence and selects it on the page, and the panel
  stays open so the next result is one key away. Matching ignores case and accents
  ("cancion" finds "canción") and treats typographic quotes like plain ones.

## [1.3.0] - 2026-09-19

### Added
- **Bookmarks and notes** in both readers: `B` (or the star in the toolbar) marks the page you
  are on, again to remove it. The side panel (`T`) now has a *Bookmarks* tab next to
  *Contents* (`Tab` switches): each bookmark shows where it is, the first words of the page
  (EPUB) and an optional note (`N` edits it, `Delete` removes the bookmark, `Enter` or a
  click jumps to it). Bookmarks are stored with the library.

### Changed
- The side panel opens even for books without a table of contents, on the bookmarks.

## [1.2.0] - 2026-09-18

Linux and macOS join Windows.

### Added
- **Linux** builds: `.deb` (Ubuntu, Debian, Mint…), `.rpm` (Fedora, openSUSE…) and a portable
  `.tar.gz`. Packages add *Devava Reader* to the applications menu.
- **macOS** builds: `.dmg` for Apple Silicon and for Intel Macs. The app is not notarized, so
  the first launch needs *Privacy & Security → Open Anyway* (see the release notes).
- On macOS the shortcuts use `⌘` instead of `Ctrl`; tooltips and hints say so.
- Every release is smoke-tested on the three systems by GitHub Actions with real input
  events; the screenshots are kept with the build.

### Changed
- The library file lives in the Documents folder of each system: `Documents\Devava Reader`
  on Windows (OneDrive-aware), `~/Documents/Devava Reader` on macOS and the XDG Documents
  folder on Linux (`~/.local/share/devava-reader` when there is none).
- The interface uses the platform's own UI font (Segoe UI, San Francisco, or the desktop's
  sans-serif) and the reader's font stacks include common Linux fonts.
- File dialogs also match upper-case `.EPUB` / `.PDF` extensions on case-sensitive systems.

## [1.1.0] - 2026-09-17

First public release.

### Added
- **PDF / manga reader**: pages rendered as images with Apache PDFBox, single or double-page
  spreads (the cover and wide pages stand alone), left-to-right or right-to-left reading
  direction, fit to the whole page or to the width, the PDF outline as table of contents,
  and progress by page. Shortcuts: `R` direction, `W` fit, `1` / `2` layout.
- Collections can mix `.epub` and `.pdf` volumes; both are accepted by the *Add books*
  dialog and by drag & drop, and PDF volumes show a **PDF** badge.
- **Library search and filters**: find collections by name, show only EPUB or only PDF
  collections, pin favourites so they stay on top, and reorder collections manually
  (`Ctrl+↑` / `Ctrl+↓`).
- Windows installer (`-setup.exe`) and portable ZIP, built by GitHub Actions from every
  version tag, with SHA-256 checksums.

### Changed
- The packaged application now ships a jlink runtime image (JDK modules + JavaFX) and runs
  from the class path, which is what makes PDFBox usable inside the bundle.
- Reader themes (light / sepia / dark) are shared by the EPUB and PDF readers.

## [1.0.0] - 2026-09-17

Initial version, not published as a release.

- EPUB reader with a two-page (or single-page) view and exact pagination, table of
  contents, footnote and internal links, font size / typeface / theme / column settings and
  progress weighted by chapter size.
- Collections of volumes with custom ordering, progress bars and read/unread status, and a
  "Continue reading" card.
- Library stored in a single JSON file under `Documents\Devava Reader`, created
  automatically; the previous Spanish library file is imported on first launch.

[1.5.0]: https://github.com/HideOnVava/devava-reader/releases/tag/v1.5.0
[1.4.0]: https://github.com/HideOnVava/devava-reader/releases/tag/v1.4.0
[1.3.0]: https://github.com/HideOnVava/devava-reader/releases/tag/v1.3.0
[1.2.0]: https://github.com/HideOnVava/devava-reader/releases/tag/v1.2.0
[1.1.0]: https://github.com/HideOnVava/devava-reader/releases/tag/v1.1.0
[1.0.0]: https://github.com/HideOnVava/devava-reader/commit/bfcfb71
