<#
.SYNOPSIS
    Smoke test of the Windows build: installs the setup.exe silently (or uses an app image),
    runs the app with the sample library, drives it through the readers with real input
    events, saves screenshots and the app log, then closes it and checks the exit code and the
    saved reading progress.

.EXAMPLE
    .\tools\smoke\smoke-windows.ps1 -Setup dist\release\Devava-Reader-1.2.0-windows-x64-setup.exe -Samples target\smoke\samples -Out target\smoke\out
    .\tools\smoke\smoke-windows.ps1 -Exe "dist\Devava Reader\Devava Reader.exe" -Samples ... -Out ...
#>
param(
    [string]$Setup = "",
    [string]$Exe = "",
    [Parameter(Mandatory = $true)][string]$Samples,
    [Parameter(Mandatory = $true)][string]$Out
)
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $Out | Out-Null
$Out = (Resolve-Path $Out).Path
$library = Join-Path (Resolve-Path $Samples).Path "library.json"

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Smoke {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
    [DllImport("dwmapi.dll")] public static extern int DwmGetWindowAttribute(IntPtr hwnd, int attr, out RECT rect, int size);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
}
"@

if ($Setup) {
    $p = Start-Process -FilePath (Resolve-Path $Setup).Path -ArgumentList "/qn" -PassThru -Wait
    if ($p.ExitCode -ne 0) { throw "Installer exit code $($p.ExitCode)" }
    $Exe = Join-Path $env:LOCALAPPDATA "Devava Reader\Devava Reader.exe"
    Write-Host "Installed: $Exe"
}
if (-not (Test-Path $Exe)) { throw "Launcher not found: $Exe" }

$env:JAVA_TOOL_OPTIONS = "-Dreader.library=$library"
$app = Start-Process -FilePath $Exe -PassThru -RedirectStandardOutput "$Out\app.log" -RedirectStandardError "$Out\app.err.log"
Remove-Item Env:\JAVA_TOOL_OPTIONS
$app.Handle | Out-Null   # cache the handle so that ExitCode is available after the process ends

# The jpackage launcher re-runs itself as a child process (with the runtime on the PATH), so
# the window belongs to a process other than $app: look it up by name and title.
function Window {
    for ($i = 0; $i -lt 60; $i++) {
        if (-not (Get-Process -Id $app.Id -ErrorAction SilentlyContinue)) { throw "The app exited before showing a window" }
        $proc = Get-Process -Name "Devava Reader" -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq "Devava Reader" } | Select-Object -First 1
        if ($proc) { return $proc.MainWindowHandle }
        Start-Sleep -Seconds 1
    }
    throw "No window appeared"
}
function Rect($h) {
    $r = New-Object Smoke+RECT
    [Smoke]::DwmGetWindowAttribute($h, 9, [ref]$r, [System.Runtime.InteropServices.Marshal]::SizeOf($r)) | Out-Null
    return $r
}
function Shot($name) {
    $h = Window; [Smoke]::SetForegroundWindow($h) | Out-Null; Start-Sleep -Milliseconds 400
    $r = Rect $h
    $bmp = New-Object System.Drawing.Bitmap ($r.Right - $r.Left), ($r.Bottom - $r.Top)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($r.Left, $r.Top, 0, 0, $bmp.Size)
    $g.Dispose(); $bmp.Save("$Out\$name.png"); $bmp.Dispose()
    Write-Host "screenshot: $name"
}
function Key($keys, $wait = 1) {
    $h = Window; [Smoke]::SetForegroundWindow($h) | Out-Null; Start-Sleep -Milliseconds 200
    [System.Windows.Forms.SendKeys]::SendWait($keys); Start-Sleep -Seconds $wait
}

Window | Out-Null; Start-Sleep -Seconds 4
# The whole flow is driven with the keyboard, so it does not depend on window size or position:
# the library focuses its list with the first collection selected, Enter opens a collection,
# a collection focuses its list with the first volume selected, Enter opens the reader and
# Escape goes back one screen.
Shot "01-library"
Key "{ENTER}"                       # open "The Lantern Road"
Shot "02-collection-novel"
Key "{DOWN}"; Key "{ENTER}" 8       # Vol. 2 -> EPUB reader
Shot "03-reader-epub"
Key "{RIGHT}"; Key "{RIGHT}"
Shot "04-reader-epub-next-pages"
Key "t"                             # contents panel
Shot "05-reader-epub-contents"
Key "{ESC}"; Key "{ESC}" 2          # close contents, back to the collection
Key "{ESC}"                         # back to the library
Key "{DOWN}"; Key "{ENTER}"         # "Sample Manga"
Shot "06-collection-manga"
Key "{ENTER}" 8                     # Vol. 1 -> PDF reader
Shot "07-reader-pdf"
Key "{LEFT}"; Key "{LEFT}"          # right-to-left: Left goes forward
Shot "08-reader-pdf-next-spread"
Key "{ESC}" 2

$windowProc = Get-Process -Name "Devava Reader" | Where-Object { $_.MainWindowTitle -eq "Devava Reader" } | Select-Object -First 1
$windowProc.CloseMainWindow() | Out-Null
if (-not $app.WaitForExit(30000)) { Stop-Process -Name "Devava Reader" -Force; throw "The app did not exit after closing the window" }
Write-Host "app exit status: $($app.ExitCode)"
if ($app.ExitCode -ne 0) { Get-Content "$Out\app.err.log"; throw "Non-zero exit code" }
$log = (Get-Content "$Out\app.log" -Raw -ErrorAction SilentlyContinue) + (Get-Content "$Out\app.err.log" -Raw -ErrorAction SilentlyContinue)
if ($log -match "(?i)exception") { Write-Host $log; throw "The log contains an exception" }
if (-not ((Get-Content $library -Raw) -match '"savedPosition": "1[2-9]:')) {
    Get-Content $library | Select-String savedPosition; throw "Reading progress was not saved"
}
Write-Host "Smoke test passed"
