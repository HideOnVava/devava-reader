#!/usr/bin/env bash
#
# Smoke test of the Linux build: starts a virtual display (Xvfb + openbox), runs the app with
# the sample library, drives it with xdotool through the main screens — library, EPUB reader,
# PDF reader — and saves screenshots plus the app log. Fails if the app crashes, logs an
# exception, or does not save the reading progress on exit.
#
# Usage: tools/smoke/smoke-linux.sh <launcher> <sample-folder> <output-folder>
#   <launcher>       e.g. "/opt/devava-reader/bin/Devava Reader"
#   <sample-folder>  output of tools/samples/SampleLibrary (contains library.json)
# Needs: xvfb, openbox, xdotool, imagemagick (apt-get install -y xvfb openbox xdotool imagemagick)

set -euo pipefail
launcher="$1"; samples="$2"; out="$3"
mkdir -p "$out"
library="$samples/library.json"

export DISPLAY=:99
Xvfb :99 -screen 0 1600x1000x24 >/dev/null 2>&1 &
sleep 2
openbox >/dev/null 2>&1 &
sleep 1

JAVA_TOOL_OPTIONS="-Dreader.library=$library" "$launcher" >"$out/app.log" 2>&1 &
app_pid=$!

# Wait for the main window (up to ~60 s).
wid=""
for _ in $(seq 1 60); do
    wid="$(xdotool search --onlyvisible --name '^Devava Reader$' 2>/dev/null | head -1 || true)"
    [[ -n "$wid" ]] && break
    kill -0 "$app_pid" 2>/dev/null || { echo "The app exited before showing a window"; cat "$out/app.log"; exit 1; }
    sleep 1
done
[[ -n "$wid" ]] || { echo "No window appeared"; cat "$out/app.log"; exit 1; }
sleep 4

shot()  { import -window root "$out/$1.png"; echo "screenshot: $1"; }
click() { xdotool mousemove --window "$wid" "$1" "$2" click 1; sleep "${3:-1}"; }
key()   { xdotool key --delay 120 "$@"; sleep 1; }

# Coordinates are relative to the 1000x680 client area of the window (see docs/screenshots).
shot 01-library
click 920 178 8                 # "Read" on the Continue reading card -> EPUB reader (Vol. 2)
shot 02-reader-epub
key Right; key Right
shot 03-reader-epub-next-pages
key t; sleep 1                  # contents panel
shot 04-reader-epub-contents
key Escape; key Escape; sleep 2 # close contents, back to the collection
shot 05-collection-novel
click 37 45 2                   # back to the library
click 299 328 1                 # select "Sample Manga"
click 928 640 2                 # Open
shot 06-collection-manga
click 299 120 1                 # select Vol. 1
click 928 640 8                 # Read -> PDF reader
shot 07-reader-pdf
key Left; key Left              # right-to-left: Left goes forward
shot 08-reader-pdf-next-spread
key Escape; sleep 2
key alt+F4; sleep 3             # close the window: the app must exit cleanly

status=0
wait "$app_pid" || status=$?
echo "app exit status: $status"
[[ $status -eq 0 ]] || { cat "$out/app.log"; exit 1; }
if grep -qi "exception" "$out/app.log"; then
    echo "The log contains an exception:"; cat "$out/app.log"; exit 1
fi
# The manga volume was opened and turned: its progress must have been saved.
grep -q '"savedPosition": "13:0.00000"\|"savedPosition":"13:0.00000"' "$library" \
    || grep -q '"savedPosition": "1[2-9]:' "$library" \
    || { echo "Reading progress was not saved:"; grep -n "savedPosition" "$library"; exit 1; }
echo "Smoke test passed"
