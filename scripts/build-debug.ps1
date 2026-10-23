$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$image = "vidplay-android-build:latest"

$imageExists = docker image inspect $image 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "La imagen de compilación no existe. Construyéndola..."
    & (Join-Path $PSScriptRoot "build-image.ps1")
    if ($LASTEXITCODE -ne 0) { throw "No se pudo construir la imagen Android." }
}

$mountProject = "type=bind,source=$root,target=/workspace"
$mountCache = "type=volume,source=vidplay-gradle-cache,target=/gradle-cache"

docker run --rm `
    --mount $mountProject `
    --mount $mountCache `
    -w /workspace `
    $image `
    bash -lc "./gradlew :app:assembleDebug --no-daemon --stacktrace"

if ($LASTEXITCODE -ne 0) { throw "La compilación falló." }

$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "No se encontró el APK: $apk" }

Write-Host "APK generado: $apk"
