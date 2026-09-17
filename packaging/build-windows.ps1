<#
.SYNOPSIS
    Builds Devava Reader as a self-contained Windows application (Lector.exe-style
    app image with an embedded Java runtime) using jpackage from the JDK.

.DESCRIPTION
    1. Compiles the project with the Maven wrapper.
    2. Runs jpackage with the module path made of target/classes plus the JavaFX and
       Gson jars from the local Maven repository (~/.m2).
    3. Leaves the result in <Destination>\Devava Reader\Devava Reader.exe.

    Requirements: JDK 21 (jpackage is part of it). No WiX is needed for an app image.
    Pass -Type exe or -Type msi to build an installer instead (requires WiX Toolset 3).

.EXAMPLE
    .\packaging\build-windows.ps1
    .\packaging\build-windows.ps1 -Destination "$env:LOCALAPPDATA\Programs" -Shortcut
#>
param(
    [string]$Destination = (Join-Path (Split-Path $PSScriptRoot -Parent) "dist"),
    [ValidateSet("app-image", "exe", "msi")]
    [string]$Type = "app-image",
    [string]$JdkHome = $env:JAVA_HOME,
    [switch]$Shortcut
)

$ErrorActionPreference = "Stop"
$projectDir = Split-Path $PSScriptRoot -Parent
$appName = "Devava Reader"
$javafxVersion = "21.0.6"
$gsonVersion = "2.10.1"

# --- Locate the JDK -----------------------------------------------------------
if (-not $JdkHome) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) { $JdkHome = Split-Path (Split-Path $javaCmd.Source -Parent) -Parent }
}
$jpackage = Join-Path $JdkHome "bin\jpackage.exe"
if (-not (Test-Path $jpackage)) {
    throw "jpackage.exe not found. Set JAVA_HOME to a JDK 21 installation (or pass -JdkHome)."
}
$env:JAVA_HOME = $JdkHome

# --- Compile -----------------------------------------------------------------
Push-Location $projectDir
try {
    & .\mvnw.cmd -q -B clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }

    # --- Module path: application classes + dependencies from the local Maven repo ---
    $m2 = Join-Path $env:USERPROFILE ".m2\repository"
    $jfx = Join-Path $m2 "org\openjfx"
    $modules = @(
        "target\classes",
        (Join-Path $m2 "com\google\code\gson\gson\$gsonVersion\gson-$gsonVersion.jar")
    )
    foreach ($m in "base", "graphics", "controls", "fxml", "web", "media") {
        $modules += Join-Path $jfx "javafx-$m\$javafxVersion\javafx-$m-$javafxVersion-win.jar"
    }
    foreach ($m in $modules) {
        if (-not (Test-Path $m)) { throw "Missing module path entry: $m (run the build once with network access)." }
    }
    $modulePath = $modules -join ";"

    # --- Package -----------------------------------------------------------------
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    $appFolder = Join-Path $Destination $appName
    if ($Type -eq "app-image" -and (Test-Path $appFolder)) {
        Write-Host "Replacing previous app image at $appFolder"
        Remove-Item -Recurse -Force $appFolder
    }

    & $jpackage `
        --type $Type `
        --name $appName `
        --app-version "1.0.0" `
        --vendor "devava XP Studios" `
        --description "A minimal desktop EPUB reader for book collections" `
        --icon (Join-Path $PSScriptRoot "icon.ico") `
        --module com.devavaxp.reader/com.devavaxp.reader.ReaderApp `
        --module-path $modulePath `
        --dest $Destination `
        --java-options "-Dfile.encoding=UTF-8"
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }

    Write-Host "Built: $appFolder"

    # --- Optional desktop shortcut -----------------------------------------------
    if ($Shortcut -and $Type -eq "app-image") {
        $exe = Join-Path $appFolder "$appName.exe"
        $desktop = [Environment]::GetFolderPath("Desktop")
        $shell = New-Object -ComObject WScript.Shell
        $link = $shell.CreateShortcut((Join-Path $desktop "$appName.lnk"))
        $link.TargetPath = $exe
        $link.WorkingDirectory = $appFolder
        $link.IconLocation = "$exe,0"
        $link.Description = "Devava Reader - EPUB reader by devava XP Studios"
        $link.Save()
        Write-Host "Desktop shortcut created: $desktop\$appName.lnk"
    }
} finally {
    Pop-Location
}
