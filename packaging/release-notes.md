## Download

{{DOWNLOADS}}

64-bit systems: Windows 10 or 11, macOS 12 or later, or a Linux desktop with GTK 3 (any
current Ubuntu, Debian, Mint, Fedora, openSUSE, Arch…). **Java is not required** — a trimmed
runtime is bundled. All files are built automatically by GitHub Actions from this tag, so the
build log shows exactly what went into them.

<details>
<summary><b>Windows: "Windows protected your PC"</b></summary>

The files are not code-signed (that needs a paid certificate), so SmartScreen may show this
warning the first time. Click **More info → Run anyway**.
</details>

<details>
<summary><b>macOS: "Devava Reader cannot be opened" / "Apple could not verify…"</b></summary>

The app is not notarized with Apple (that needs a paid developer account), so macOS blocks it
once. After the first attempt to open it, go to **System Settings → Privacy & Security**,
scroll down to the message about *Devava Reader* and click **Open Anyway**, then confirm. On
macOS 13 and 14 you can instead right-click the app and choose **Open**. This is only needed
the first time.
</details>

<details>
<summary><b>Linux: where things go</b></summary>

The `.deb` / `.rpm` packages install to `/opt/devava-reader` and add *Devava Reader* to the
applications menu; remove them with `sudo apt remove devava-reader` (or `dnf`). The `.tar.gz`
needs no installation: extract it and run `Devava Reader/bin/Devava Reader`. The library is
kept in your Documents folder (`Devava Reader/library.json`).
</details>

Your library (collections, volumes and reading progress) is kept when you update or uninstall,
and the book files themselves are never moved or modified.

## What's new in {{VERSION}}

{{CHANGES}}

## Verify the download (optional)

Compare the SHA-256 of the file (`certutil -hashfile <file> SHA256` on Windows,
`shasum -a 256 <file>` on macOS, `sha256sum <file>` on Linux) with:

```
{{CHECKSUMS}}
```
