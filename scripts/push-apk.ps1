$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ADB) { $env:ADB } else { "C:\Android\platform-tools\adb.exe" }
$apk = Join-Path $root "app\build\outputs\apk\play\debug\app-play-debug.apk"

if (-not (Test-Path $apk)) { throw "No se encontró el APK. Ejecuta build-debug.ps1 primero." }

& $adb push $apk /sdcard/Download/VidCofre-debug.apk
if ($LASTEXITCODE -ne 0) { throw "No se pudo copiar el APK al teléfono." }

Write-Host "APK copiado a Descargas/VidCofre-debug.apk"
Write-Host "En el teléfono: Archivos > Descargas > VidCofre-debug.apk > Instalar."
