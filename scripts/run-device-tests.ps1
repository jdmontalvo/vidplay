$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$adb = if ($env:ADB) { $env:ADB } else { "C:\Android\platform-tools\adb.exe" }
$appApk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $root "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"

if (-not (Test-Path $adb)) { throw "No se encontró adb en $adb" }
if (-not (Test-Path $appApk)) { throw "Falta el APK debug. Ejecuta build-debug.ps1" }
if (-not (Test-Path $testApk)) {
    throw "Falta el APK de tests. Ejecuta: docker ... ./gradlew :app:assembleDebugAndroidTest"
}

$devices = & $adb devices
if ($devices -notmatch "\sdevice\s*$") {
    throw "No hay dispositivo conectado y autorizado. Conecta el teléfono por USB."
}

& $adb install -r $appApk
if ($LASTEXITCODE -ne 0) { throw "No se pudo instalar el APK debug." }

& $adb install -r $testApk
if ($LASTEXITCODE -ne 0) { throw "No se pudo instalar el APK de tests." }

& $adb shell am instrument -w -r `
    -e class com.vidplay.app.VaultKeyManagerTest `
    com.vidplay.app.test/androidx.test.runner.AndroidJUnitRunner

if ($LASTEXITCODE -ne 0) { throw "Los tests instrumentados fallaron." }
Write-Host "Tests instrumentados completados."
