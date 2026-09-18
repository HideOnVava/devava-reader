#!/usr/bin/env bash
#
# Builds Devava Reader for Linux or macOS with jlink and jpackage from the JDK — the
# counterpart of build-windows.ps1.
#
#   1. Compiles the project with the Maven wrapper and copies its dependencies.
#   2. Builds a trimmed Java runtime with jlink (packaging/jdk-modules.txt + JavaFX).
#   3. Runs jpackage with that runtime and the application jars on the class path, producing
#      the app image in target/package/image ("Devava Reader" on Linux, "Devava Reader.app"
#      on macOS). PDFBox ships as automatic modules, which jlink cannot link, so the app
#      itself runs from the class path; JavaFX lives in the runtime image as proper modules.
#   4. --type app-image (default): copies the image to <dest>.
#      --type deb | rpm (Linux) or dmg | pkg (macOS): builds an installer from the image
#      into <dest>. deb needs fakeroot, rpm needs rpmbuild; dmg needs nothing extra.
#
# Requirements: a full JDK 21 (with the jmods folder), found through --jdk-home, JAVA_HOME
# or the java on the PATH. The version number is read from pom.xml.
#
# Usage: packaging/build-unix.sh [--type TYPE] [--dest DIR] [--jdk-home DIR] [--reuse-image]

set -euo pipefail

app_name="Devava Reader"
vendor="devava XP Studios"
description="A minimal desktop reader for EPUB books and PDF manga collections"
about_url="https://github.com/HideOnVava/devava-reader"
maintainer="HideOnVava <HideOnVava@users.noreply.github.com>"
javafx_modules="javafx.base,javafx.graphics,javafx.controls,javafx.fxml,javafx.web,javafx.media"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "$script_dir/.." && pwd)"
type="app-image"
dest="$project_dir/dist"
jdk_home="${JAVA_HOME:-}"
reuse_image=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --type) type="$2"; shift 2 ;;
        --dest) dest="$2"; shift 2 ;;
        --jdk-home) jdk_home="$2"; shift 2 ;;
        --reuse-image) reuse_image=true; shift ;;
        -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
        *) echo "Unknown option: $1" >&2; exit 2 ;;
    esac
done

# --- Platform ---------------------------------------------------------------------
case "$(uname -s)" in
    Linux) os="linux" ;;
    Darwin) os="mac" ;;
    *) echo "Unsupported system: $(uname -s). Use build-windows.ps1 on Windows." >&2; exit 2 ;;
esac
case "$(uname -m)" in
    x86_64|amd64) arch="x64"; classifier="$os" ;;
    arm64|aarch64) arch="arm64"; classifier="$os-aarch64" ;;
    *) echo "Unsupported architecture: $(uname -m)" >&2; exit 2 ;;
esac
case "$type" in
    app-image) ;;
    deb|rpm) [[ "$os" == "linux" ]] || { echo "--type $type is only available on Linux" >&2; exit 2; } ;;
    dmg|pkg) [[ "$os" == "mac" ]] || { echo "--type $type is only available on macOS" >&2; exit 2; } ;;
    *) echo "Unknown --type $type (app-image, deb, rpm, dmg, pkg)" >&2; exit 2 ;;
esac

# --- JDK ------------------------------------------------------------------------------
if [[ -z "$jdk_home" ]]; then
    if [[ "$os" == "mac" ]] && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
        jdk_home="$(/usr/libexec/java_home -v 21)"
    elif command -v java >/dev/null 2>&1; then
        java_bin="$(readlink -f "$(command -v java)" 2>/dev/null || command -v java)"
        jdk_home="$(cd "$(dirname "$java_bin")/.." && pwd)"
    fi
fi
for tool in jlink jpackage; do
    [[ -x "$jdk_home/bin/$tool" ]] || { echo "$tool not found in $jdk_home/bin. Set JAVA_HOME to a full JDK 21 (or pass --jdk-home)." >&2; exit 1; }
done
[[ -d "$jdk_home/jmods" ]] || { echo "$jdk_home has no jmods folder: a full JDK 21 is required." >&2; exit 1; }
export JAVA_HOME="$jdk_home"

app_version="$(sed -n 's/^    <version>\(.*\)<\/version>$/\1/p' "$project_dir/pom.xml" | head -1)"
[[ -n "$app_version" ]] || { echo "Could not read the version from pom.xml" >&2; exit 1; }
jdk_modules="$(grep -v '^#' "$script_dir/jdk-modules.txt" | grep -v '^\s*$' | paste -sd, -)"

work="$project_dir/target/package"
image_dir="$work/image"
if [[ "$os" == "mac" ]]; then image="$image_dir/$app_name.app"; icon="$script_dir/icon.icns"; else image="$image_dir/$app_name"; icon="$script_dir/icon.png"; fi

cd "$project_dir"

if $reuse_image && [[ -d "$image" ]]; then
    echo "Reusing the app image at $image"
else
    # --- Compile ---------------------------------------------------------------------
    ./mvnw -q -B clean package -DskipTests
    ./mvnw -q -B dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency

    app_jar="$(ls target/devava-reader-*.jar | head -1)"
    [[ -f "$app_jar" ]] || { echo "Application jar not found in target/" >&2; exit 1; }

    # JavaFX platform jars go into the runtime image; every other library goes on the class path.
    javafx_jars=(target/dependency/javafx-*-"$classifier".jar)
    [[ ${#javafx_jars[@]} -eq 6 ]] || { echo "Expected 6 JavaFX '$classifier' jars in target/dependency, found ${#javafx_jars[@]}" >&2; exit 1; }

    rm -rf "$work"
    mkdir -p "$work/input" "$image_dir"
    cp "$app_jar" "$work/input/"
    for jar in target/dependency/*.jar; do
        [[ "$(basename "$jar")" == javafx-* ]] || cp "$jar" "$work/input/"
    done

    # --- Runtime image: JDK modules + JavaFX -----------------------------------------
    module_path="$jdk_home/jmods"
    for jar in "${javafx_jars[@]}"; do module_path="$module_path:$jar"; done
    "$jdk_home/bin/jlink" \
        --module-path "$module_path" \
        --add-modules "$jdk_modules,$javafx_modules" \
        --output "$work/runtime" \
        --strip-debug --no-header-files --no-man-pages --compress zip-6

    # --- App image ---------------------------------------------------------------------
    mac_opts=()
    if [[ "$os" == "mac" ]]; then
        mac_opts=(--mac-package-identifier com.devavaxp.reader --mac-package-name "$app_name")
    fi
    "$jdk_home/bin/jpackage" \
        --type app-image \
        --name "$app_name" \
        --app-version "$app_version" \
        --vendor "$vendor" \
        --copyright "Copyright (c) 2026 $vendor" \
        --description "$description" \
        --icon "$icon" \
        --runtime-image "$work/runtime" \
        --input "$work/input" \
        --main-jar "$(basename "$app_jar")" \
        --main-class com.devavaxp.reader.Launcher \
        --dest "$image_dir" \
        --java-options "-Dfile.encoding=UTF-8" \
        "${mac_opts[@]}"
fi

mkdir -p "$dest"

if [[ "$type" == "app-image" ]]; then
    target="$dest/$(basename "$image")"
    if [[ -e "$target" ]]; then
        echo "Replacing previous app image at $target"
        rm -rf "$target"
    fi
    cp -R "$image" "$target"
    echo "Built: $target"
else
    # --- Installer from the app image -----------------------------------------------------
    if [[ "$os" == "linux" ]]; then
        extra=(--linux-package-name devava-reader
               --linux-shortcut
               --linux-menu-group "Office;Viewer;"
               --linux-app-category misc
               --license-file "$project_dir/LICENSE")
        [[ "$type" == "deb" ]] && extra+=(--linux-deb-maintainer "$maintainer")
        [[ "$type" == "rpm" ]] && extra+=(--linux-rpm-license-type MIT)
    else
        extra=(--mac-package-identifier com.devavaxp.reader)
    fi
    "$jdk_home/bin/jpackage" \
        --type "$type" \
        --app-image "$image" \
        --name "$app_name" \
        --app-version "$app_version" \
        --vendor "$vendor" \
        --copyright "Copyright (c) 2026 $vendor" \
        --description "$description" \
        --about-url "$about_url" \
        --dest "$dest" \
        "${extra[@]}"
    echo "Built: $(ls -t "$dest"/*."$type" | head -1)"
fi
