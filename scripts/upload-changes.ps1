<#
.SYNOPSIS
    Prepara, confirma y sube todos los cambios del repositorio actual.

.DESCRIPTION
    Ejecuta el flujo completo desde la raíz del repositorio:
      1. git add -A
      2. comprueba que no se suban APK, AAB, vídeos, WAV, PCM, baúles ni claves privadas
      3. crea un commit (mensaje automático o -Message)
      4. hace git push de la rama actual

.PARAMETER Message
    Mensaje del commit. Si se omite, usa "Actualización automática <fecha>".

.PARAMETER Remote
    Remoto de Git. Por defecto: origin.

.PARAMETER NoPush
    Crea el commit pero no sube nada.

.EXAMPLE
    .\scripts\upload-changes.ps1 -Message "Añade el reproductor"
#>
param(
    [string]$Message = "",
    [string]$Remote = "origin",
    [switch]$NoPush
)

$ErrorActionPreference = "Stop"

function Fail([string]$Text) {
    Write-Host ""
    Write-Host "ERROR: $Text" -ForegroundColor Red
    exit 1
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

& git rev-parse --is-inside-work-tree | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "Esta carpeta no es un repositorio Git: $repoRoot" }

$branch = (& git rev-parse --abbrev-ref HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($branch) -or $branch -eq "HEAD") {
    Fail "No se pudo determinar la rama actual. Evita el estado detached HEAD."
}

Write-Host "Repositorio: $repoRoot" -ForegroundColor Cyan
Write-Host "Rama:        $branch" -ForegroundColor Cyan
Write-Host "Remoto:      $Remote" -ForegroundColor Cyan

& git add -A
if ($LASTEXITCODE -ne 0) { Fail "No se pudieron preparar los cambios con git add -A." }

$staged = @(& git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { Fail "No se pudo leer la lista de archivos preparados." }

$forbidden = @($staged | Where-Object {
    $_ -match '\.(wav|pcm|apk|aab|mp4|mkv|webm|avi|mov|flv|m2ts|vpv|vpt)$' -or
    $_ -match '(^|/)local\.properties$' -or
    $_ -match '(^|/)keystore\.properties$' -or
    ($_ -match '\.jks$' -and $_ -notmatch '^keystore/vidplay-debug\.jks$')
})

if ($forbidden.Count -gt 0) {
    & git reset | Out-Null
    Write-Host ""
    Write-Host "Se canceló el commit. Estos archivos no deben subirse:" -ForegroundColor Red
    $forbidden | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    exit 1
}

$hasChanges = $false
& git diff --cached --quiet
if ($LASTEXITCODE -eq 1) {
    $hasChanges = $true
} elseif ($LASTEXITCODE -ne 0) {
    Fail "No se pudo comprobar si hay cambios preparados."
}

if ($hasChanges) {
    Write-Host ""
    Write-Host "Archivos preparados:" -ForegroundColor Yellow
    & git diff --cached --stat

    if ([string]::IsNullOrWhiteSpace($Message)) {
        $Message = "Actualización automática $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    }

    & git commit -m $Message
    if ($LASTEXITCODE -ne 0) { Fail "git commit falló. Revisa el estado del repositorio." }
} else {
    Write-Host ""
    Write-Host "No hay cambios nuevos para confirmar." -ForegroundColor Yellow
}

if ($NoPush) {
    Write-Host ""
    Write-Host "Commit listo. No se subió nada porque usaste -NoPush." -ForegroundColor Yellow
    & git status --short --branch
    exit 0
}

$remoteRef = "$Remote/$branch"
& git rev-parse --verify --quiet $remoteRef | Out-Null
if ($LASTEXITCODE -eq 0) {
    $ahead = [int](& git rev-list --count "$remoteRef..HEAD")
    if ($LASTEXITCODE -ne 0) { Fail "No se pudo comprobar si hay commits pendientes de subir." }
    if ($ahead -eq 0) {
        Write-Host ""
        Write-Host "Todo está sincronizado; no hay commits nuevos para subir." -ForegroundColor Green
        & git status --short --branch
        exit 0
    }
    Write-Host ""
    Write-Host "Commits pendientes de subir: $ahead" -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "La rama remota $remoteRef todavía no existe; se creará al subir." -ForegroundColor Yellow
}

& git push $Remote $branch
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Fail "git push falló. Si el remoto tiene cambios, ejecuta: git pull --rebase $Remote $branch"
}

Write-Host ""
Write-Host "Cambios subidos correctamente a $Remote/$branch." -ForegroundColor Green
& git status --short --branch
& git log -1 --oneline
