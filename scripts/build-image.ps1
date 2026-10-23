$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    docker build -f tools/docker/android-build.Dockerfile -t vidplay-android-build:latest .
    if ($LASTEXITCODE -ne 0) { throw "Docker build failed." }
}
finally {
    Pop-Location
}
