#!/usr/bin/env bash
#
# Smoke test of the Linux build: starts a virtual display (Xvfb + openbox), runs the app with
# the sample library, drives it with xdotool through the main screens — library, EPUB reader,
# PDF reader — and saves screenshots plus the app log. Fails if the app crashes, logs an
# exception, does not close cleanly, or does not save the reading progress on exit.
#
# Usage: tools/smoke/smoke-linux.sh <launcher> <sample-folder> <output-folder>
#   <launcher>       e.g. "/opt/devava-reader/bin/Devava Reader"
#   <sample-folder>  output of tools/samples/SampleLibrary (contains library.json)
# Needs: xvfb, openbox, xdotool, wmctrl, imagemagick
#        (apt-get install -y xvfb openbox xdotool wmctrl imagemagick)

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
echo "window $wid: $(xdotool getwindowgeometry "$wid" | tr '\n' ' ')"
xprop -id "$wid" WM_NORMAL_HINTS _NET_WM_STATE 2>/dev/null || true
geometry="$(xdotool getwindowgeometry "$wid" | sed -n 's/.*Geometry: //p')"
if [[ "${geometry%x*}" -lt 990 ]]; then
    echo "WARNING: the window opened at $geometry instead of about 1000x680; resizing it for the test"
    xdotool windowsize "$wid" 1000 680
    sleep 2
fi

# One Stage means one X window, but it is looked up again before every action so that the
# test keeps working if JavaFX ever re-creates it (for example when leaving a maximized reader).
win()   { xdotool search --onlyvisible --name '^Devava Reader$' | head -1; }
shot()  { import -window root "$out/$1.png"; echo "screenshot: $1 ($(xdotool getwindowgeometry "$(win)" | sed -n 's/.*Geometry: //p'))"; }
key()   { xdotool windowactivate --sync "$(win)" key --delay 120 "$@"; sleep "${WAIT:-1}"; }

# The whole flow is driven with the keyboard, so it does not depend on window size or position:
# the library focuses its list with the first collection selected, Enter opens a collection,
# a collection focuses its list with the first volume selected, Enter opens the reader and
# Escape goes back one screen.
shot 01-library
key Return                      # open "The Lantern Road"
shot 02-collection-novel
key Down; WAIT=8 key Return     # Vol. 2 -> EPUB reader
shot 03-reader-epub
key Right; key Right
shot 04-reader-epub-next-pages
key b                           # bookmark this page
key t; key Tab                  # side panel: contents, then the bookmarks tab
shot 05-reader-epub-bookmarks
key Escape; WAIT=2 key Escape   # close the panel, back to the collection
key Escape                      # back to the library
key Down; key Return            # "Sample Manga"
shot 06-collection-manga
WAIT=8 key Return               # Vol. 1 -> PDF reader
shot 07-reader-pdf
key Left; key Left              # right-to-left: Left goes forward
shot 08-reader-pdf-next-spread
WAIT=2 key Escape

# Close the window the way a user would (WM close request); the app must exit cleanly.
wmctrl -i -c "$(win)"
status=""
for _ in $(seq 1 30); do
    if ! kill -0 "$app_pid" 2>/dev/null; then
        wait "$app_pid" && status=0 || status=$?
        break
    fi
    sleep 1
done
if [[ -z "$status" ]]; then
    echo "The app did not exit after the close request. Windows:"; wmctrl -l || true
    kill "$app_pid" || true
    cat "$out/app.log"; exit 1
fi
echo "app exit status: $status"
[[ $status -eq 0 ]] || { cat "$out/app.log"; exit 1; }
if grep -qi "exception" "$out/app.log"; then
    echo "The log contains an exception:"; cat "$out/app.log"; exit 1
fi
# The manga volume was opened and turned: its progress must have been saved.
grep -q '"savedPosition": "1[2-9]:' "$library" \
    || { echo "Reading progress was not saved:"; grep -n "savedPosition" "$library"; exit 1; }
# The bookmark set in the EPUB reader must have been saved with the first words of its page.
grep -q '"excerpt": "[A-Za-z]' "$library" \
    || { echo "The bookmark was not saved:"; grep -n -e excerpt -e bookmarks "$library"; exit 1; }
echo "Smoke test passed"
