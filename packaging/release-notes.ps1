<#
.SYNOPSIS
    Assembles the text of a GitHub release from the files built on every platform.

.DESCRIPTION
    Looks for Devava-Reader-<version>-* files anywhere under -Dir (the workflow downloads one
    folder per platform into it), computes their SHA-256 checksums, and writes into -Dir:
      SHA256SUMS.txt      one line per file, sha256sum format
      RELEASE_NOTES.md    release-notes.md template + a download table grouped by system
                          + the "## [<version>]" section of CHANGELOG.md + the checksums
    Runs under Windows PowerShell 5.1 and PowerShell 7 on any system.

.EXAMPLE
    .\packaging\release-notes.ps1 -Dir dist\release
#>
param(
    [string]$Dir = (Join-Path (Split-Path $PSScriptRoot -Parent) "dist\release"),
    [string]$Version = ([xml](Get-Content (Join-Path (Split-Path $PSScriptRoot -Parent) "pom.xml"))).project.version
)

$ErrorActionPreference = "Stop"
$projectDir = Split-Path $PSScriptRoot -Parent
$repo = "HideOnVava/devava-reader"
$base = "https://github.com/$repo/releases/download/v$Version"

# --- Changelog section for this version --------------------------------------
$changelog = Get-Content (Join-Path $projectDir "CHANGELOG.md") -Raw -Encoding UTF8
$pattern = "(?s)## \[" + [regex]::Escape($Version) + "\][^\r\n]*\r?\n(.*?)(?=\r?\n## \[|\z)"
$section = [regex]::Match($changelog, $pattern)
if (-not $section.Success) { throw "CHANGELOG.md has no '## [$Version]' section." }
$changes = $section.Groups[1].Value.Trim()

# --- Files: what each one is, in the order the table shows them ------------------
$kinds = @(
    @{ Suffix = '-windows-x64-setup.exe';    Os = 'Windows'; Text = '**Recommended.** Installer: double-click, keep or change the folder, done. No administrator rights needed; uninstall from *Settings > Apps*.' },
    @{ Suffix = '-windows-x64-portable.zip'; Os = 'Windows'; Text = 'No installation: unzip anywhere and run `Devava Reader.exe`.' },
    @{ Suffix = '-macos-arm64.dmg';          Os = 'macOS';   Text = '**Apple Silicon** Macs (M1 or later). Open the image and drag *Devava Reader* to *Applications*.' },
    @{ Suffix = '-macos-x64.dmg';            Os = 'macOS';   Text = '**Intel** Macs. Open the image and drag *Devava Reader* to *Applications*.' },
    @{ Suffix = '-linux-x64.deb';            Os = 'Linux';   Text = 'Ubuntu, Debian, Linux Mint and other .deb systems: double-click it or run `sudo apt install ./<file>`. Adds a menu entry.' },
    @{ Suffix = '-linux-x64.rpm';            Os = 'Linux';   Text = 'Fedora, openSUSE and other .rpm systems: `sudo dnf install ./<file>` (or `zypper`).' },
    @{ Suffix = '-linux-x64.tar.gz';         Os = 'Linux';   Text = 'Any 64-bit distribution, no installation: `tar xf <file>` and run `"Devava Reader/bin/Devava Reader"`.' }
)
$found = Get-ChildItem $Dir -Recurse -File -Filter "Devava-Reader-$Version-*"
if (-not $found) { throw "No Devava-Reader-$Version-* files under $Dir." }

$rows = @()
$sums = @()
foreach ($kind in $kinds) {
    $file = $found | Where-Object { $_.Name -eq "Devava-Reader-$Version$($kind.Suffix)" } | Select-Object -First 1
    if (-not $file) { continue }
    $text = $kind.Text.Replace("<file>", $file.Name)
    $rows += "| $($kind.Os) | [$($file.Name)]($base/$($file.Name)) | $text |"
    $sums += "{0}  {1}" -f (Get-FileHash $file.FullName -Algorithm SHA256).Hash.ToLower(), $file.Name
}
$known = $kinds | ForEach-Object { "Devava-Reader-$Version$($_.Suffix)" }
$unknown = $found | Where-Object { $known -notcontains $_.Name }
if ($unknown) { throw "Unexpected release files: $($unknown.Name -join ', ')" }

$table = @("| System | File | Notes |", "| --- | --- | --- |") + $rows

# --- Output ---------------------------------------------------------------------
$utf8 = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText((Join-Path $Dir "SHA256SUMS.txt"), ($sums -join "`n") + "`n", $utf8)

$notes = Get-Content (Join-Path $PSScriptRoot "release-notes.md") -Raw -Encoding UTF8
$notes = $notes.Replace("{{VERSION}}", $Version).Replace("{{DOWNLOADS}}", ($table -join "`n"))
$notes = $notes.Replace("{{CHANGES}}", $changes).Replace("{{CHECKSUMS}}", ($sums -join "`n"))
[System.IO.File]::WriteAllText((Join-Path $Dir "RELEASE_NOTES.md"), $notes, $utf8)

Write-Host "Release notes for $Version with $($rows.Count) file(s):"
$rows | ForEach-Object { Write-Host "  $_" }
