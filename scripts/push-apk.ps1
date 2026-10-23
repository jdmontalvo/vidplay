$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ADB) { $env:ADB } else { "C:\Android\platform-tools\adb.exe" }
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apk)) { throw "No se encontró el APK. Ejecuta build-debug.ps1 primero." }

& $adb push $apk /sdcard/Download/VidPlay-debug.apk
if ($LASTEXITCODE -ne 0) { throw "No se pudo copiar el APK al teléfono." }

Write-Host "APK copiado a Descargas/VidPlay-debug.apk"
Write-Host "En el teléfono: Archivos > Descargas > VidPlay-debug.apk > Instalar."
