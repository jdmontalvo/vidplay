param(
    [switch]$KeepOld
)

$ErrorActionPreference = "Stop"

# Personal, ad-free build for the author. It is signed with the upload key,
# uses the ".personal" applicationId (separate install, separate vault) and
# lives in .\personal\, which is ignored by Git. Never upload this folder.

$root = Split-Path -Parent $PSScriptRoot
$image = "vidplay-android-build:latest"
$properties = Join-Path $root "keystore.properties"

if (-not (Test-Path $properties)) {
    throw "Falta keystore.properties. Ejecuta primero .\scripts\create-upload-keystore.ps1"
}

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
    bash -lc "./gradlew :app:assembleFossRelease --no-daemon --stacktrace"

if ($LASTEXITCODE -ne 0) { throw "La compilación personal falló." }

$apk = Join-Path $root "app\build\outputs\apk\foss\release\app-foss-release.apk"
if (-not (Test-Path $apk)) { throw "No se encontró el APK: $apk" }

$outputDir = Join-Path $root "personal"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$versionLine = Select-String -Path (Join-Path $root "app\build.gradle.kts") -Pattern 'versionName = "([^"]+)"'
$version = if ($versionLine) { $versionLine.Matches[0].Groups[1].Value } else { "dev" }
$target = Join-Path $outputDir "VidCofre-personal-$version.apk"

if ((Test-Path $target) -and $KeepOld) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $target = Join-Path $outputDir "VidCofre-personal-$version-$stamp.apk"
}

Copy-Item $apk $target -Force

Write-Host ""
Write-Host "APK personal sin anuncios: $target"
Write-Host "Paquete: com.vidplay.app.personal (instalacion independiente, sin INTERNET)."
Write-Host "Esta carpeta (personal\) esta en .gitignore: no se sube al repositorio."
