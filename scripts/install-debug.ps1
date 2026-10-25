$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ADB) { $env:ADB } else { "C:\Android\platform-tools\adb.exe" }
$apk = Join-Path $root "app\build\outputs\apk\play\debug\app-play-debug.apk"

if (-not (Test-Path $adb)) { throw "No se encontró adb en $adb" }
if (-not (Test-Path $apk)) { throw "No se encontró el APK en $apk. Ejecuta primero build-debug.ps1" }

& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw "adb install falló." }

& $adb shell am start -n com.vidplay.app/.MainActivity
if ($LASTEXITCODE -ne 0) { throw "No se pudo iniciar la app." }

Write-Host "VidPlay instalada y lanzada."
