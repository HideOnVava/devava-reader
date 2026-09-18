<#
.SYNOPSIS
    Builds the Windows release files into dist\release (the counterpart of release.sh):
      Devava-Reader-<version>-windows-x64-setup.exe      installer (needs the WiX Toolset 3.x)
      Devava-Reader-<version>-windows-x64-portable.zip   the app image, zipped
      SHA256SUMS.txt                                     checksums of both

.DESCRIPTION
    The version comes from pom.xml and must have a "## [<version>]" section in CHANGELOG.md.
    The release notes are assembled by release-notes.ps1 once every platform has built (the
    GitHub Actions workflow does that; see .github/workflows/release.yml).

.EXAMPLE
    .\packaging\release.ps1
    .\packaging\release.ps1 -SkipInstaller      # only the ZIP (no WiX needed)
#>
param(
    [string]$JdkHome = $env:JAVA_HOME,
    [switch]$SkipInstaller,
    [string]$Output = (Join-Path (Split-Path $PSScriptRoot -Parent) "dist\release")
)

$ErrorActionPreference = "Stop"
$projectDir = Split-Path $PSScriptRoot -Parent
$appName = "Devava Reader"
$version = ([xml](Get-Content (Join-Path $projectDir "pom.xml"))).project.version
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw "pom.xml version '$version' is not a release version (expected x.y.z)." }
$changelog = Get-Content (Join-Path $projectDir "CHANGELOG.md") -Raw -Encoding UTF8
if ($changelog -notmatch "(?m)^## \[$([regex]::Escape($version))\]") {
    throw "CHANGELOG.md has no '## [$version]' section. Add one before releasing."
}

# --- Build ---------------------------------------------------------------------
$build = Join-Path $PSScriptRoot "build-windows.ps1"
$stage = Join-Path $projectDir "target\package\dist"
$zipName = "Devava-Reader-$version-windows-x64-portable.zip"
$setupName = "Devava-Reader-$version-windows-x64-setup.exe"

if (Test-Path $Output) { Remove-Item -Recurse -Force $Output }
New-Item -ItemType Directory -Force -Path $Output | Out-Null

& $build -JdkHome $JdkHome -Type app-image -Destination $stage

# Portable ZIP of the app image: one top-level folder, forward slashes in the entry names
# (ZipFile::CreateFromDirectory would use backslashes under Windows PowerShell 5.1).
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$zipPath = Join-Path $Output $zipName
$root = (Get-Item (Join-Path $stage $appName)).FullName
$zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem $root -Recurse -File) {
        $entry = "$appName/" + $file.FullName.Substring($root.Length + 1).Replace("\", "/")
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $file.FullName, $entry,
            [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $zip.Dispose()
}
Write-Host "Zipped: $zipPath"

if (-not $SkipInstaller) {
    & $build -JdkHome $JdkHome -Type exe -ReuseImage -Destination $stage
    Move-Item (Join-Path $stage "$appName-$version.exe") (Join-Path $Output $setupName) -Force
    Write-Host "Installer: $(Join-Path $Output $setupName)"
}

# --- Checksums (same format as sha256sum) --------------------------------------------
$files = Get-ChildItem $Output -File -Filter "Devava-Reader-*"
$sums = $files | ForEach-Object { "{0}  {1}" -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower(), $_.Name }
$utf8 = New-Object System.Text.UTF8Encoding $false   # no BOM, whichever PowerShell runs this
[System.IO.File]::WriteAllText((Join-Path $Output "SHA256SUMS.txt"), ($sums -join "`n") + "`n", $utf8)

Write-Host ""
Write-Host "Release files for $appName $version in $Output`:"
Get-ChildItem $Output | ForEach-Object { Write-Host ("  {0,-50} {1,10:N0} KB" -f $_.Name, ($_.Length / 1KB)) }
