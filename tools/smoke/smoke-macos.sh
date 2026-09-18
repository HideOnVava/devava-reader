#!/usr/bin/env bash
#
# Smoke test of the macOS build: runs the app bundle with the sample library, takes screen
# captures, drives it through the readers when the session allows UI automation (System
# Events needs the Accessibility permission, which hosted runners may not grant), and quits
# it with an Apple event. Fails if the app crashes or logs an exception.
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

# Wait for the window (up to ~60 s); "System Events" can list processes without extra permissions.
for _ in $(seq 1 60); do
    if osascript -e 'tell application "System Events" to (exists process "Devava Reader")' 2>/dev/null | grep -q true; then
        break
    fi
    kill -0 "$app_pid" 2>/dev/null || { echo "The app exited early"; cat "$out/app.log"; exit 1; }
    sleep 1
done
sleep 8

shot() { screencapture -x "$out/$1.png"; echo "screenshot: $1"; }
shot 01-library

# Optional UI automation: window position via Accessibility, then clicks/keys relative to it.
automation=false
if pos="$(osascript -e 'tell application "System Events" to tell process "Devava Reader" to get position of window 1' 2>/dev/null)"; then
    automation=true
    wx="${pos%%,*}"; wy="${pos##*, }"; wy="${wy//[[:space:]]/}"
    click() { osascript -e "tell application \"System Events\" to click at {$((wx + $1)), $((wy + $2))}"; sleep "${3:-1}"; }
    key()   { osascript -e "tell application \"System Events\" to key code $1"; sleep 1; }
    # Coordinates are relative to the 1000x680 content area; the title bar is 28 px high.
    click 920 206 8                 # "Read" on the Continue reading card -> EPUB reader (Vol. 2)
    shot 02-reader-epub
    key 124; key 124                # Right arrow x2
    shot 03-reader-epub-next-pages
    key 53; sleep 2                 # Escape -> collection
    click 37 73 2                   # back to the library
    click 299 356 1                 # select "Sample Manga"
    click 928 668 2                 # Open
    click 299 148 1                 # select Vol. 1
    click 928 668 8                 # Read -> PDF reader
    shot 07-reader-pdf
    key 123; key 123                # Left arrow x2 (right-to-left: forward)
    shot 08-reader-pdf-next-spread
    key 53; sleep 2
else
    echo "UI automation is not available in this session (Accessibility); only the launch was captured."
fi

osascript -e 'tell application "Devava Reader" to quit' || true
status=0
wait "$app_pid" || status=$?
echo "app exit status: $status"
[[ $status -eq 0 ]] || { cat "$out/app.log"; exit 1; }
if grep -qi "exception" "$out/app.log"; then
    echo "The log contains an exception:"; cat "$out/app.log"; exit 1
fi
if $automation; then
    grep -q '"savedPosition": "1[2-9]:' "$library" || { echo "Reading progress was not saved:"; grep -n savedPosition "$library"; exit 1; }
fi
echo "Smoke test passed (automation: $automation)"
