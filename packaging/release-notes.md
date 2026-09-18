## Download

| File | When to use it |
| --- | --- |
| **[{{SETUP}}]({{SETUP_URL}})** | **Recommended.** Double-click it, keep or change the folder, done. Adds Start menu and desktop shortcuts (you can untick them) and can be removed from *Settings → Apps*. No administrator rights needed. |
| [{{ZIP}}]({{ZIP_URL}}) | No installation: unzip it anywhere (a USB stick works too) and run `Devava Reader.exe`. |

Requirements: Windows 10 or 11, 64-bit. **Java is not required** — a trimmed runtime is bundled.

> **"Windows protected your PC"?** The files are not code-signed (that needs a paid certificate), so SmartScreen may show this warning the first time. Click **More info → Run anyway**. Both files are built automatically by GitHub Actions from this tag, so the build log shows exactly what went into them.

Your library (collections, volumes and reading progress) lives in `Documents\Devava Reader\library.json`; it is kept when you update or uninstall, and the book files themselves are never moved or modified.

## What's new in {{VERSION}}

{{CHANGES}}

## Verify the download (optional)

In a terminal run `certutil -hashfile <file> SHA256` and compare the result with:

```
{{CHECKSUMS}}
```
