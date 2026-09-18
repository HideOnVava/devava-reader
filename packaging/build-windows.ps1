<#
.SYNOPSIS
    Builds Devava Reader as a self-contained Windows application using jlink and jpackage
    from the JDK: an app image (a folder with Devava Reader.exe and an embedded Java runtime)
    or, from that image, an installer (.exe / .msi).

.DESCRIPTION
    Steps:
    1. Compiles the project with the Maven wrapper and copies its dependencies.
    2. Builds a trimmed Java runtime with jlink: the JDK modules the app needs plus JavaFX.
    3. Runs jpackage with that runtime and the application jars on the class path, producing
       the app image in target\package\image\Devava Reader.
       (PDFBox ships as automatic modules, which jlink cannot link, so the app itself runs
       from the class path; JavaFX still lives in the runtime image as proper modules.)
    4. -Type app-image (default): copies the image to <Destination>\Devava Reader.
       -Type exe / msi: builds a per-user installer from the image into <Destination>.
       Installers need the WiX Toolset 3.x (https://github.com/wixtoolset/wix3/releases);
       jpackage finds it through the PATH or the WIX environment variable.

    Requirements: a full JDK 21 (with the jmods folder). The version number is read from
    pom.xml so it is defined in one place.

.EXAMPLE
    .\packaging\build-windows.ps1
    .\packaging\build-windows.ps1 -Destination "$env:LOCALAPPDATA\Programs" -Shortcut
    .\packaging\build-windows.ps1 -Type exe
    .\packaging\build-windows.ps1 -Type exe -ReuseImage     # installer from the last image
#>
param(
    [string]$Destination = (Join-Path (Split-Path $PSScriptRoot -Parent) "dist"),
    [ValidateSet("app-image", "exe", "msi")]
    [string]$Type = "app-image",
    [string]$JdkHome = $env:JAVA_HOME,
    [switch]$Shortcut,
    [switch]$ReuseImage
)

$ErrorActionPreference = "Stop"
$projectDir = Split-Path $PSScriptRoot -Parent
$appName = "Devava Reader"
$vendor = "devava XP Studios"
$description = "A minimal desktop reader for EPUB books and PDF manga collections"
$appVersion = ([xml](Get-Content (Join-Path $projectDir "pom.xml"))).project.version
# Identifies the product across versions: a newer installer replaces an older installation
# instead of sitting next to it. Never change it.
$upgradeUuid = "7d3a9d0e-5c1b-4a9e-9f2c-3b6d1e8f4a21"

# JDK modules the application needs at run time: packaging/jdk-modules.txt (shared with
# build-unix.sh). JavaFX pulls in the rest transitively.
$jdkModules = @(Get-Content (Join-Path $PSScriptRoot "jdk-modules.txt") |
    ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith("#") })
$javafxModules = @("javafx.base", "javafx.graphics", "javafx.controls", "javafx.fxml", "javafx.web", "javafx.media")

$work = Join-Path $projectDir "target\package"
$image = Join-Path $work "image\$appName"

# --- Locate the JDK -----------------------------------------------------------
if (-not $JdkHome) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) { $JdkHome = Split-Path (Split-Path $javaCmd.Source -Parent) -Parent }
}
$jlink = Join-Path $JdkHome "bin\jlink.exe"
$jpackage = Join-Path $JdkHome "bin\jpackage.exe"
$jmods = Join-Path $JdkHome "jmods"
if (-not (Test-Path $jpackage) -or -not (Test-Path $jlink) -or -not (Test-Path $jmods)) {
    throw "jlink/jpackage/jmods not found. Set JAVA_HOME to a full JDK 21 installation (or pass -JdkHome)."
}
$env:JAVA_HOME = $JdkHome

# --- WiX (only for installers) --------------------------------------------------
if ($Type -ne "app-image") {
    if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
        $wixBin = if ($env:WIX) { Join-Path $env:WIX "bin" } else { "C:\Program Files (x86)\WiX Toolset v3.14\bin" }
        if (Test-Path (Join-Path $wixBin "candle.exe")) {
            $env:PATH = "$wixBin;$env:PATH"
        } else {
            throw "The WiX Toolset 3.x is required to build a -Type $Type installer (candle.exe not found). Install it from https://github.com/wixtoolset/wix3/releases or build the app image instead."
        }
    }
}

Push-Location $projectDir
try {
    if ($ReuseImage -and (Test-Path (Join-Path $image "$appName.exe"))) {
        Write-Host "Reusing the app image at $image"
    } else {
        # --- Compile ---------------------------------------------------------------
        & .\mvnw.cmd -q -B clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }
        & .\mvnw.cmd -q -B dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target\dependency
        if ($LASTEXITCODE -ne 0) { throw "Could not copy the dependencies." }

        $appJar = Get-ChildItem "target\devava-reader-*.jar" | Select-Object -First 1
        if (-not $appJar) { throw "Application jar not found in target\." }

        # JavaFX platform jars go into the runtime image; every other library goes on the class path.
        $javafxJars = Get-ChildItem "target\dependency\javafx-*-win.jar"
        $libraryJars = Get-ChildItem "target\dependency\*.jar" | Where-Object { $_.Name -notlike "javafx-*" }
        if ($javafxJars.Count -ne $javafxModules.Count) {
            throw "Expected $($javafxModules.Count) JavaFX platform jars in target\dependency, found $($javafxJars.Count)."
        }

        if (Test-Path $work) { Remove-Item -Recurse -Force $work }
        $inputDir = Join-Path $work "input"
        $runtime = Join-Path $work "runtime"
        New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
        Copy-Item $appJar.FullName $inputDir
        foreach ($jar in $libraryJars) { Copy-Item $jar.FullName $inputDir }

        # --- Runtime image: JDK modules + JavaFX -----------------------------------
        $jlinkModulePath = @($jmods) + ($javafxJars | ForEach-Object { $_.FullName })
        & $jlink `
            --module-path ($jlinkModulePath -join ";") `
            --add-modules (($jdkModules + $javafxModules) -join ",") `
            --output $runtime `
            --strip-debug --no-header-files --no-man-pages --compress zip-6
        if ($LASTEXITCODE -ne 0) { throw "jlink failed." }

        # --- App image ---------------------------------------------------------------
        & $jpackage `
            --type app-image `
            --name $appName `
            --app-version $appVersion `
            --vendor $vendor `
            --copyright "Copyright (c) 2026 $vendor" `
            --description $description `
            --icon (Join-Path $PSScriptRoot "icon.ico") `
            --runtime-image $runtime `
            --input $inputDir `
            --main-jar $appJar.Name `
            --main-class com.devavaxp.reader.Launcher `
            --dest (Split-Path $image -Parent) `
            --java-options "-Dfile.encoding=UTF-8"
        if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }
    }

    New-Item -ItemType Directory -Force -Path $Destination | Out-Null

    if ($Type -eq "app-image") {
        $appFolder = Join-Path $Destination $appName
        if (Test-Path $appFolder) {
            Write-Host "Replacing previous app image at $appFolder"
            Remove-Item -Recurse -Force $appFolder
        }
        Copy-Item -Recurse $image $appFolder
        Write-Host "Built: $appFolder"

        # --- Optional desktop shortcut -------------------------------------------
        if ($Shortcut) {
            $exe = Join-Path $appFolder "$appName.exe"
            $desktop = [Environment]::GetFolderPath("Desktop")
            $shell = New-Object -ComObject WScript.Shell
            $link = $shell.CreateShortcut((Join-Path $desktop "$appName.lnk"))
            $link.TargetPath = $exe
            $link.WorkingDirectory = $appFolder
            $link.IconLocation = "$exe,0"
            $link.Description = "$appName - EPUB and PDF reader by $vendor"
            $link.Save()
            Write-Host "Desktop shortcut created: $desktop\$appName.lnk"
        }
    } else {
        # --- Installer from the app image -------------------------------------------
        # Per-user: no administrator rights, installs under %LOCALAPPDATA%. The user can pick
        # the folder and whether to create the Start menu / desktop shortcuts.
        & $jpackage `
            --type $Type `
            --app-image $image `
            --name $appName `
            --app-version $appVersion `
            --vendor $vendor `
            --copyright "Copyright (c) 2026 $vendor" `
            --description $description `
            --about-url "https://github.com/HideOnVava/devava-reader" `
            --win-per-user-install `
            --win-dir-chooser `
            --win-menu --win-menu-group $vendor `
            --win-shortcut --win-shortcut-prompt `
            --win-upgrade-uuid $upgradeUuid `
            --dest $Destination
        if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }
        $installer = Get-ChildItem (Join-Path $Destination "$appName-$appVersion.$Type")
        Write-Host "Built: $($installer.FullName)"
    }
} finally {
    Pop-Location
}
