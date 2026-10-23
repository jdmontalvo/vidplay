$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$keystore = Join-Path $root "keystore\vidplay-debug.jks"
$keytool = @(
    "C:\Program Files\Eclipse Adoptium\jre-17.0.16.8-hotspot\bin\keytool.exe",
    "C:\Program Files\Java\jdk-11.0.2\bin\keytool.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $keytool) {
    $command = Get-Command keytool -ErrorAction SilentlyContinue
    if ($command) { $keytool = $command.Source }
}
if (-not $keytool) { throw "No se encontró keytool. Instala un JDK." }
if (Test-Path $keystore) {
    Write-Host "La clave de depuración ya existe: $keystore"
    exit 0
}

New-Item -ItemType Directory -Force -Path (Split-Path $keystore) | Out-Null
& $keytool -genkeypair -v `
    -keystore $keystore `
    -storepass vidplay `
    -alias vidplay-debug `
    -keypass vidplay `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -dname "CN=VidPlay Debug, OU=Development, O=VidPlay, L=Local, ST=Local, C=US"

if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la clave de depuración." }
Write-Host "Clave creada: $keystore"
