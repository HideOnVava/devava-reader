#!/usr/bin/env bash
#
# Builds the release files of this platform into dist/release — the counterpart of
# release.ps1 for Linux and macOS:
#   Linux:  Devava-Reader-<version>-linux-<arch>.tar.gz   the app image, as a tarball
#           Devava-Reader-<version>-linux-<arch>.deb      Debian/Ubuntu package
#           Devava-Reader-<version>-linux-<arch>.rpm      Fedora/openSUSE package (only when rpmbuild exists)
#   macOS:  Devava-Reader-<version>-macos-<arch>.dmg      disk image with "Devava Reader.app"
#   both:   SHA256SUMS.txt                                 checksums of the files above
#
# The release notes are assembled later, once every platform has built, by release-notes.ps1
# (the GitHub Actions workflow does this; see .github/workflows/release.yml).
#
# Usage: packaging/release.sh [--jdk-home DIR] [--output DIR]

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "$script_dir/.." && pwd)"
output="$project_dir/dist/release"
jdk_args=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --jdk-home) jdk_args=(--jdk-home "$2"); shift 2 ;;
        --output) output="$2"; shift 2 ;;
        *) echo "Unknown option: $1" >&2; exit 2 ;;
    esac
done

case "$(uname -s)" in
    Linux) os="linux" ;;
    Darwin) os="macos" ;;
    *) echo "Unsupported system: $(uname -s)" >&2; exit 2 ;;
esac
case "$(uname -m)" in
    x86_64|amd64) arch="x64" ;;
    arm64|aarch64) arch="arm64" ;;
    *) echo "Unsupported architecture: $(uname -m)" >&2; exit 2 ;;
esac

version="$(sed -n 's/^    <version>\(.*\)<\/version>$/\1/p' "$project_dir/pom.xml" | head -1)"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "pom.xml version '$version' is not a release version (x.y.z)" >&2; exit 1; }
grep -q "^## \[$version\]" "$project_dir/CHANGELOG.md" || { echo "CHANGELOG.md has no '## [$version]' section. Add one before releasing." >&2; exit 1; }

build="$script_dir/build-unix.sh"
stage="$project_dir/target/package/dist"
base="Devava-Reader-$version-$os-$arch"

rm -rf "$output" "$stage"
mkdir -p "$output"

"$build" "${jdk_args[@]}" --type app-image --dest "$stage"

if [[ "$os" == "linux" ]]; then
    # Portable tarball: one top-level folder, run bin/"Devava Reader" inside it.
    tar -C "$stage" -czf "$output/$base.tar.gz" "Devava Reader"
    echo "Tarball: $output/$base.tar.gz"

    "$build" "${jdk_args[@]}" --type deb --reuse-image --dest "$stage"
    mv "$stage"/*.deb "$output/$base.deb"
    echo "Package: $output/$base.deb"

    if command -v rpmbuild >/dev/null 2>&1; then
        "$build" "${jdk_args[@]}" --type rpm --reuse-image --dest "$stage"
        mv "$stage"/*.rpm "$output/$base.rpm"
        echo "Package: $output/$base.rpm"
    else
        echo "rpmbuild not found: skipping the .rpm package"
    fi
else
    "$build" "${jdk_args[@]}" --type dmg --reuse-image --dest "$stage"
    mv "$stage"/*.dmg "$output/$base.dmg"
    echo "Disk image: $output/$base.dmg"
fi

# --- Checksums (same format as sha256sum) --------------------------------------------
cd "$output"
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum Devava-Reader-* > SHA256SUMS.txt
else
    shasum -a 256 Devava-Reader-* > SHA256SUMS.txt
fi

echo
echo "Release files for Devava Reader $version in $output:"
ls -l "$output"
