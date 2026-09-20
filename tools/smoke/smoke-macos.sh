#!/usr/bin/env bash
#
# Smoke test of the macOS build: runs the app bundle with the sample library, takes screen
# captures, drives it through the readers with System Events key codes (which need the
# Accessibility permission; GitHub's macOS runners grant it), and quits it.
# Fails if the app crashes, logs an exception, or does not save the reading progress.
#
# Usage: tools/smoke/smoke-macos.sh <app-bundle> <sample-folder> <output-folder>
#   <app-bundle>     e.g. "/Applications/Devava Reader.app"
#   <sample-folder>  output of tools/samples/SampleLibrary (contains library.json)

set -euo pipefail
app="$1"; samples="$2"; out="$3"
mkdir -p "$out"
library="$samples/library.json"

# Hosted runners have a small 1024x768 display: hide the Dock so that the whole window fits.
defaults write com.apple.dock autohide -bool true && killall Dock 2>/dev/null || true
sleep 2

JAVA_TOOL_OPTIONS="-Dreader.library=$library" "$app/Contents/MacOS/Devava Reader" >"$out/app.log" 2>&1 &
app_pid=$!

# Wait for the window (up to ~60 s).
for _ in $(seq 1 60); do
    if osascript -e 'tell application "System Events" to (exists window 1 of process "Devava Reader")' 2>/dev/null | grep -q true; then
        break
    fi
    kill -0 "$app_pid" 2>/dev/null || { echo "The app exited early"; cat "$out/app.log"; exit 1; }
    sleep 1
done
sleep 6

se() { osascript -e 'tell application "System Events" to tell process "Devava Reader"' "$@" -e 'end tell'; }
shot() { screencapture -x "$out/$1.png"; echo "screenshot: $1"; }
key() { se -e 'set frontmost to true' -e "key code $1" >/dev/null; sleep "${WAIT:-1}"; }
cmd_key() { se -e 'set frontmost to true' -e "keystroke \"$1\" using command down" >/dev/null; sleep "${WAIT:-1}"; }
cmd_shift_key() { se -e 'set frontmost to true' -e "keystroke \"$1\" using {command down, shift down}" >/dev/null; sleep "${WAIT:-1}"; }
# Whether the system folder panel is still open (as a window of its own or as a sheet).
panel_open() { se -e 'set n to count of windows' -e 'set s to exists sheet 1 of window 1' -e 'return (n > 1) or s' 2>/dev/null | grep -q true; }
type_() { se -e 'set frontmost to true' -e "keystroke \"$1\"" >/dev/null; sleep "${WAIT:-1}"; }

automation=true
if ! se -e 'set frontmost to true' >/dev/null 2>&1; then
    automation=false
    echo "UI automation is not available in this session (Accessibility); only the launch is checked."
fi

# The whole flow is driven with the keyboard, so it does not depend on window size or position
# (see smoke-linux.sh). Key codes: Return 36, Down 125, Escape 53, Right 124, Left 123, t 17, b 11, Tab 48, Space 49.
shot 01-library
if $automation; then
    key 36                          # open "The Lantern Road"
    shot 02-collection-novel
    key 125; WAIT=8 key 36          # Vol. 2 -> EPUB reader
    shot 03-reader-epub
    key 124; key 124
    shot 04-reader-epub-next-pages
    key 11                          # B: bookmark this page
    key 17; key 48                  # T then Tab: side panel on the bookmarks tab
    shot 05-reader-epub-bookmarks
    cmd_key f; type_ ferryman       # search inside the book
    WAIT=3 key 36                   # jump to the first hit (another chapter)
    shot 06-reader-epub-search
    key 53; WAIT=2 key 53           # close the panel, back to the collection
    key 53                          # back to the library
    key 125; key 36                 # "Sample Manga"
    shot 07-collection-manga
    WAIT=8 key 36                   # Vol. 1 -> PDF reader
    shot 08-reader-pdf
    key 123; key 123                # right-to-left: Left goes forward
    shot 09-reader-pdf-next-spread
    WAIT=4 key 53                   # leave the reader and let its background work finish
    key 53                          # back to the library
    # Import a folder as a collection: Tab reaches "Import folder…" and Space presses it (on
    # macOS, JavaFX buttons ignore Return); Cmd+Shift+G asks the folder panel for a path, Return
    # goes there and Return again chooses it (if the panel is still open).
    key 48; key 48; key 48; WAIT=3 key 49
    panel_open || { echo "The folder panel did not open"; shot 10-no-panel; exit 1; }
    cmd_shift_key g; type_ "$(cd "$samples" && pwd)/Lantern Import"
    shot 10a-folder-panel
    WAIT=3 key 36
    if panel_open; then WAIT=3 key 36; fi
    shot 10-library-imported
fi

# Quit through the application menu (Cmd+Q), which closes the window and saves.
se -e 'set frontmost to true' -e 'keystroke "q" using command down' >/dev/null 2>&1 || osascript -e 'tell application "Devava Reader" to quit' >/dev/null 2>&1 || true
status=""
for _ in $(seq 1 30); do
    if ! kill -0 "$app_pid" 2>/dev/null; then
        wait "$app_pid" && status=0 || status=$?
        break
    fi
    sleep 1
done
if [[ -z "$status" ]]; then
    echo "The app did not quit"; kill "$app_pid" || true; cat "$out/app.log"; exit 1
fi
echo "app exit status: $status"
[[ $status -eq 0 ]] || { cat "$out/app.log"; exit 1; }
# JavaFX on macOS may print a bare 'Exception in thread "InvokeLaterDispatcher"' header (no
# stack trace) while the process is already terminating after Cmd+Q; anything else is a failure.
if grep -i "exception" "$out/app.log" | grep -qv '^Exception in thread "InvokeLaterDispatcher" *$'; then
    echo "The log contains an exception:"; cat "$out/app.log"; exit 1
fi
if $automation; then
    grep -q '"savedPosition": "1[2-9]:' "$library" || { echo "Reading progress was not saved:"; grep -n savedPosition "$library"; exit 1; }
    grep -q '"excerpt": "[A-Za-z]' "$library" || { echo "The bookmark was not saved:"; grep -n -e excerpt -e bookmarks "$library"; exit 1; }
    grep -q '"title": "Lantern Import"' "$library" || { echo "The folder was not imported as a collection:"; grep -n '"title"' "$library"; exit 1; }
fi
echo "Smoke test passed (automation: $automation)"
