param(
    [switch]$Apk
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$image = "vidplay-android-build:latest"
$properties = Join-Path $root "keystore.properties"

if (-not (Test-Path $properties)) {
    throw "Falta keystore.properties. Ejecuta primero .\scripts\create-upload-keystore.ps1"
}

$imageExists = docker image inspect $image 2>$null
if ($LASTEXITCODE -ne 0) {
    & (Join-Path $PSScriptRoot "build-image.ps1")
    if ($LASTEXITCODE -ne 0) { throw "No se pudo construir la imagen Android." }
}

$mountProject = "type=bind,source=$root,target=/workspace"
$mountCache = "type=volume,source=vidplay-gradle-cache,target=/gradle-cache"
$task = if ($Apk) { ":app:assemblePlayRelease" } else { ":app:bundlePlayRelease" }

docker run --rm `
    --mount $mountProject `
    --mount $mountCache `
    -w /workspace `
    $image `
    bash -lc "./gradlew $task --no-daemon --stacktrace"

if ($LASTEXITCODE -ne 0) { throw "La compilación release falló." }

if ($Apk) {
    $artifact = Join-Path $root "app\build\outputs\apk\play\release\app-play-release.apk"
} else {
    $artifact = Join-Path $root "app\build\outputs\bundle\playRelease\app-play-release.aab"
}
if (-not (Test-Path $artifact)) { throw "No se encontró el artefacto: $artifact" }
Write-Host "Artefacto release generado: $artifact"

if (-not (Test-Path (Join-Path $root "ads.properties"))) {
    Write-Host ""
    Write-Host "AVISO: falta ads.properties. Este AAB compila con los anuncios DESACTIVADOS." -ForegroundColor Yellow
    Write-Host "Copia ads.properties.example a ads.properties, rellena tus IDs de AdMob y vuelve a compilar." -ForegroundColor Yellow
}
