#!/usr/bin/env bash
#
# Smoke test of the macOS build: runs the app bundle with the sample library, takes screen
# captures, drives it through the readers with System Events (mouse clicks and key codes,
# which need the Accessibility permission — GitHub's macOS runners grant it), and quits it.
# Fails if the app crashes, logs an exception, or does not save the reading progress.
#
# Usage: tools/smoke/smoke-macos.sh <app-bundle> <sample-folder> <output-folder>
#   <app-bundle>     e.g. "/Applications/Devava Reader.app"
#   <sample-folder>  output of tools/samples/SampleLibrary (contains library.json)

set -euo pipefail
app="$1"; samples="$2"; out="$3"
mkdir -p "$out"
library="$samples/library.json"

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
# Coordinates are relative to the 1000x680 content area; the window position is read before
# every click because the readers maximize the window and restore it afterwards.
click() {
    local pos wx wy
    pos="$(se -e 'set frontmost to true' -e 'get position of window 1')"
    wx="${pos%%,*}"; wy="${pos##*, }"; wy="${wy//[[:space:]]/}"
    se -e "click at {$((wx + $1)), $((wy + 28 + $2))}" >/dev/null
    sleep "${3:-1}"
}
key() { se -e 'set frontmost to true' -e "key code $1" >/dev/null; sleep 1; }

automation=true
if ! se -e 'get position of window 1' >/dev/null 2>&1; then
    automation=false
    echo "UI automation is not available in this session (Accessibility); only the launch is checked."
fi

shot 01-library
if $automation; then
    click 920 178 8                 # "Read" on the Continue reading card -> EPUB reader (Vol. 2)
    shot 02-reader-epub
    key 124; key 124                # Right arrow x2
    shot 03-reader-epub-next-pages
    key 17; sleep 1                 # T: contents panel
    shot 04-reader-epub-contents
    key 53; key 53; sleep 2         # Escape x2: close contents, back to the collection
    shot 05-collection-novel
    click 37 45 2                   # back to the library
    click 299 328 1                 # select "Sample Manga"
    click 928 640 2                 # Open
    shot 06-collection-manga
    click 299 120 1                 # select Vol. 1
    click 928 640 8                 # Read -> PDF reader
    shot 07-reader-pdf
    key 123; key 123                # Left arrow x2 (right-to-left: forward)
    shot 08-reader-pdf-next-spread
    key 53; sleep 2
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
if grep -qi "exception" "$out/app.log"; then
    echo "The log contains an exception:"; cat "$out/app.log"; exit 1
fi
if $automation; then
    grep -q '"savedPosition": "1[2-9]:' "$library" || { echo "Reading progress was not saved:"; grep -n savedPosition "$library"; exit 1; }
fi
echo "Smoke test passed (automation: $automation)"
