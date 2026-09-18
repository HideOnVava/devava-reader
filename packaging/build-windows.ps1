<#
.SYNOPSIS
    Builds Devava Reader as a self-contained Windows application (an app image with an
    embedded Java runtime) using jlink and jpackage from the JDK.

.DESCRIPTION
    1. Compiles the project with the Maven wrapper and copies its dependencies.
    2. Builds a trimmed Java runtime with jlink: the JDK modules the app needs plus JavaFX.
    3. Runs jpackage with that runtime and the application jars on the class path.
       (PDFBox ships as automatic modules, which jlink cannot link, so the app itself runs
       from the class path; JavaFX still lives in the runtime image as proper modules.)
    4. Leaves the result in <Destination>\Devava Reader\Devava Reader.exe.

    Requirements: JDK 21 with jmods (jlink and jpackage are part of it). No WiX is needed
    for an app image. Pass -Type exe or -Type msi to build an installer instead (requires
    WiX Toolset 3).

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
$appVersion = "1.1.0"
$javafxVersion = "21.0.6"

# JDK modules the application needs at run time (JavaFX pulls in the rest transitively).
$jdkModules = @(
    "java.base", "java.desktop", "java.logging", "java.xml", "java.net.http", "java.scripting",
    "java.sql", "jdk.jsobject", "jdk.unsupported", "jdk.xml.dom", "jdk.charsets", "jdk.crypto.ec"
)
$javafxModules = @("javafx.base", "javafx.graphics", "javafx.controls", "javafx.fxml", "javafx.web", "javafx.media")

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

# --- Compile -----------------------------------------------------------------
Push-Location $projectDir
try {
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
        throw "Expected $($javafxModules.Count) JavaFX $javafxVersion platform jars in target\dependency, found $($javafxJars.Count)."
    }

    $work = Join-Path $projectDir "target\package"
    if (Test-Path $work) { Remove-Item -Recurse -Force $work }
    $input = Join-Path $work "input"
    $runtime = Join-Path $work "runtime"
    New-Item -ItemType Directory -Force -Path $input | Out-Null
    Copy-Item $appJar.FullName $input
    foreach ($jar in $libraryJars) { Copy-Item $jar.FullName $input }

    # --- Runtime image: JDK modules + JavaFX -------------------------------------
    $jlinkModulePath = @($jmods) + ($javafxJars | ForEach-Object { $_.FullName })
    & $jlink `
        --module-path ($jlinkModulePath -join ";") `
        --add-modules (($jdkModules + $javafxModules) -join ",") `
        --output $runtime `
        --strip-debug --no-header-files --no-man-pages --compress zip-6
    if ($LASTEXITCODE -ne 0) { throw "jlink failed." }

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
        --app-version $appVersion `
        --vendor "devava XP Studios" `
        --description "A minimal desktop reader for EPUB books and PDF manga collections" `
        --icon (Join-Path $PSScriptRoot "icon.ico") `
        --runtime-image $runtime `
        --input $input `
        --main-jar $appJar.Name `
        --main-class com.devavaxp.reader.Launcher `
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
        $link.Description = "Devava Reader - EPUB and PDF reader by devava XP Studios"
        $link.Save()
        Write-Host "Desktop shortcut created: $desktop\$appName.lnk"
    }
} finally {
    Pop-Location
}
