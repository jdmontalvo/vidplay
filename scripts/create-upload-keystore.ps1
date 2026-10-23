param(
    [string]$KeystorePath = "keystore/vidplay-upload.jks",
    [string]$Alias = "vidplay-upload",
    [string]$Password = ""
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$keystore = if ([System.IO.Path]::IsPathRooted($KeystorePath)) {
    $KeystorePath
} else {
    Join-Path $root $KeystorePath
}
$properties = Join-Path $root "keystore.properties"
$keytool = @(
    "C:\Program Files\Eclipse Adoptium\jre-17.0.16.8-hotspot\bin\keytool.exe",
    "C:\Program Files\Java\jdk-11.0.2\bin\keytool.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $keytool) {
    $command = Get-Command keytool -ErrorAction SilentlyContinue
    if ($command) { $keytool = $command.Source }
}
if (-not $keytool) { throw "No se encontró keytool. Instala un JDK." }
if (Test-Path $keystore) { throw "La clave de release ya existe: $keystore" }
if (Test-Path $properties) { throw "Ya existe keystore.properties. Elimínalo o renómbralo antes de crear otra clave." }

if ([string]::IsNullOrWhiteSpace($Password)) {
    $secure = Read-Host "Contraseña del keystore de release" -AsSecureString
    $confirm = Read-Host "Repite la contraseña" -AsSecureString
    $plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    )
    $plain2 = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($confirm)
    )
    if ($plain -ne $plain2) { throw "Las contraseñas no coinciden." }
    $Password = $plain
}
if ($Password.Length -lt 8) { throw "Usa una contraseña de al menos 8 caracteres." }

New-Item -ItemType Directory -Force -Path (Split-Path $keystore) | Out-Null
& $keytool -genkeypair -v `
    -keystore $keystore `
    -storepass $Password `
    -alias $Alias `
    -keypass $Password `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -dname "CN=VidPlay Upload, OU=Release, O=VidPlay, L=Local, ST=Local, C=US"

if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la clave de release." }

$relative = $keystore.Substring($root.Length).TrimStart('\', '/').Replace('\', '/')
$content = @"
storeFile=$relative
storePassword=$Password
keyAlias=$Alias
keyPassword=$Password
"@
[System.IO.File]::WriteAllText($properties, $content, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "Clave de release creada: $keystore"
Write-Host "Propiedades: $properties (ignorado por Git)"
Write-Host "IMPORTANTE: guarda una copia segura de ambos archivos. Sin ellos no podrás actualizar la app en Play Store."
