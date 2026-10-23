# AGENTS.md — VidPlay

Guía de continuación del proyecto. Léela completa antes de cambiar código.

## Qué es

**VidPlay** es un reproductor de vídeo local para Android con un baúl cifrado.

- Nombre: VidPlay
- Application ID: `com.vidplay.app`
- Versión: `1.0.0` (`versionCode 1`)
- Repositorio: <https://github.com/jdmontalvo/vidplay> (público, GPL-3.0)
- SDK: compile/target 36, min 26
- Probado en: POCO F7 Ultra (Android 16, HyperOS 3), dispositivo `fc914e16`
- Builds verificados: debug (43,5 MB) y release AAB (16,8 MB) desde Docker
- Release keystore y `keystore.properties` generados localmente (ignorados por Git); el usuario debe respaldarlos

## Estado

Implementado en código:

- Biblioteca MediaStore con permisos modernos, acceso parcial Android 14, búsqueda, ordenación, carpetas, miniaturas Coil y apertura SAF.
- Reproductor Media3 + NextLib, PiP, notificación multimedia, subtítulos, pistas de audio, velocidad, relación de aspecto, rotación, temporizador, brillo/volumen, reanudación.
- Gestos: un toque, doble toque izquierda/centro/derecha, mantener pulsado para acelerar, pellizco 1x–5x, paneo libre, deslizamiento para buscar/brillo/volumen, bloqueo.
- Baúl: AES-256-GCM por bloques, DEK envuelta por Keystore y PIN, miniaturas cifradas, `VaultDataSource` para Media3, importación con WorkManager en primer plano, borrado del original con diálogo MediaStore/SAF, restauración con verificación SHA-256.
- Bloqueo con biometría/credencial, PIN de recuperación, auto-bloqueo en segundo plano, `FLAG_SECURE`, títulos genéricos en notificaciones del baúl, PiP del baúl desactivado por defecto.
- Docker, scripts PowerShell, keystore de depuración fijo, build release/AAB, GPL-3.0 y documentación.

Validado en dispositivo (2026-10-23):

- Instalación del APK debug, arranque y navegación principal (Vídeos, Carpetas, Baúl, Ajustes).
- Tarjeta de permiso de vídeo y UI principal.
- Reproducción de un vídeo real de MediaStore abierto con `ACTION_VIEW`: Media3 usó el decodificador hardware `c2.qti.avc.decoder` y `c2.android.aac.decoder`; sesión multimedia en estado PLAYING y notificación multimedia.
- Creación de las claves `.dek`/`.pin` del baúl en el almacenamiento privado.
- La primera importación por WorkManager falló con “Baúl bloqueado” porque el proceso se reinició. Se corrigió con `VaultRepository.unlockForBackgroundWork()`, que desenvuelve la DEK del Keystore para trabajos ya iniciados por el usuario. Pendiente revalidar.

Pendiente de validar en dispositivo:

- Importación del baúl después del arreglo de desbloqueo en segundo plano, borrado del original y restauración.
- Biometría, PIN y Keystore con bloqueo/desbloqueo real.
- Gestos (doble toque, mantener pulsado, pellizco/paneo) y conflicto entre la capa Compose de controles y la vista nativa de gestos.
- Matriz de formatos (HEVC, AV1, MKV, AVI, FLV, TS, audio AC3/DTS).
- Archivos grandes, cancelación/reanudación, SAF y proveedores que no borran.
- 4K/HDR, PiP, segundo plano prolongado y consumo.
- Ficha de Play Store, Data Safety, prueba interna y publicación.

## Herramientas

| Componente | Versión |
|---|---|
| Docker | 29.8.1 probado |
| Imagen | `vidplay-android-build:latest` (Ubuntu 24.04) |
| JDK | 17 |
| Android SDK | 36 |
| Build tools | 36.0.0 |
| NDK | 27.2.12479018 |
| CMake | 3.22.1 |
| Gradle | 8.13 |
| AGP | 8.13.2 |
| Kotlin | 2.2.21 |
| KSP | 2.2.21-2.0.5 |
| Media3 | 1.11.1 |
| NextLib | 1.11.1-0.16.0 |
| Compose BOM | 2026.09.00 |
| Room | 2.8.5 |
| DataStore | 1.3.0 |
| Coil | 3.6.3 |
| WorkManager | 2.12.0 |
| Biometric | 1.4.0-alpha07 |
| ABIs | arm64-v8a, armeabi-v7a |

## Compilar e instalar

```powershell
.\scripts\build-image.ps1
.\scripts\build-debug.ps1
.\scripts\install-debug.ps1
```

Salida: `app\build\outputs\apk\debug\app-debug.apk`.

Release:

```powershell
.\scripts\create-upload-keystore.ps1
.\scripts\build-release.ps1          # AAB
.\scripts\build-release.ps1 -Apk     # APK universal de prueba
```

`keystore.properties`, `*.jks` (salvo `keystore/vidplay-debug.jks`), APK, AAB, vídeos, WAV, PCM y archivos del baúl están fuera de Git.

## Estructura

```text
app/src/main/java/com/vidplay/app/
  MainActivity.kt              # navegación Compose (Vídeos, Carpetas, Baúl, Ajustes)
  VidPlayApp.kt                # contenedor, canales, Coil, auto-bloqueo
  data/
    db/                        # Room: VaultItem, PendingDelete, PlaybackPosition
    media/                     # MediaStore, permisos, carpetas, borrado
    settings/                  # DataStore
    vault/                     # VaultCrypto, VaultKeyManager, VaultRepository,
                               # VaultDataSource, workers, monitor
  player/
    PlayerActivity.kt          # MediaController, gestos, PiP, pistas, subtítulos
    PlaybackService.kt         # MediaSessionService + ExoPlayer + NextLib
    PlayerOverlay.kt           # controles Compose
    ZoomablePlayerLayout.kt    # zoom/paneo + gestos
    PlayerUiState.kt
    PipActionReceiver.kt
  ui/
    library/                   # biblioteca y ViewModel
    folders/                   # vista de carpetas
    vault/                     # baúl, biometría, PIN, import/export
    settings/                  # ajustes y avisos
    components/                # tarjetas, miniaturas, estados vacíos
    theme/
```

## Baúl: formato y seguridad

- Ruta: `filesDir/vault/<uuid>.vpv` (almacenamiento interno privado).
- Miniaturas: `<uuid>.vpt`, cifradas, cargadas solo en memoria.
- Cabecera de 56 bytes: magic `VPVAULT1`, versión, flags, tamaño de bloque, tamaño plano y SHA-256.
- Bloques de 1 MiB: `IV aleatorio (12 B) + ciphertext + tag GCM (16 B)`.
- Clave de datos (DEK) aleatoria de 256 bits. Se guarda envuelta por:
  - una clave AES del Android Keystore (`.dek`);
  - una clave derivada del PIN con PBKDF2-HMAC-SHA256 (210 000 iteraciones, `.pin`).
- El PIN es el camino de recuperación. La biometría es la puerta de acceso de la app; la clave de Keystore no está ligada a autenticación para evitar pérdida de datos si cambian la biometría o el bloqueo de pantalla.
- `VaultDataSource` descifra el bloque solicitado y permite seek. Nunca se escribe texto plano en disco.
- La exportación verifica el SHA-256 y solo entonces borra la copia cifrada.
- `allowBackup=false`, `dataExtractionRules` excluye todo, `FLAG_SECURE` en el baúl y en la reproducción de contenido del baúl, notificaciones con título genérico.
- No hay FileProvider, share ni rutas expuestas del baúl.

**Riesgos aceptados:** root/ADB, pérdida de datos al desinstalar/borrar datos, copias previas en nubes, proveedores SAF que no permiten borrar el original, y que la puerta biométrica es a nivel de app (la clave sigue protegida por el Keystore).

## Reproductor

- `PlaybackService` construye `ExoPlayer` con `NextRenderersFactory` y `EXTENSION_RENDERER_MODE_ON` (hardware primero, FFmpeg si el formato no es soportado).
- `DefaultDataSource.Factory` usa `VaultDataSource.Factory` como base para el esquema `vault://`.
- `PlayerActivity` conecta un `MediaController`; las peticiones de reproducción llegan por extras (`EXTRA_URIS`, `EXTRA_TITLES`, `EXTRA_INDEX`, `EXTRA_START_POSITION`) o por `ACTION_VIEW`.
- El título real del baúl se muestra en la UI, pero el `MediaMetadata` de la notificación usa “Vídeo protegido” cuando `hideVaultNotification` está activo.
- `ZoomablePlayerLayout` transforma el `PlayerView` (TextureView) con escala 1–5 y traslación limitada; el contenedor recorta.
- Conflicto conocido a validar: la `ComposeView` superpuesta a pantalla completa y los gestos de la vista nativa. Si en dispositivo los gestos no llegan con los controles visibles, ocultar la `ComposeView` cuando `controlsVisible=false` o mover los gestos a Compose.

## Formatos

Media3 aporta extractores para MP4/M4V/MOV, MKV, WebM, AVI, FLV, TS/M2TS, MPEG/MPG, 3GP, OGG. NextLib añade decodificación software de H.264, HEVC, VP8, VP9, AV1 y audio AC3/EAC3/DTS/TrueHD/ALAC/AMR, entre otros.

No se garantizan todos los códecs legacy (RMVB, WMV/ASF, algunos MPEG-4 Part 2) en todos los dispositivos. Si un archivo falla, la UI muestra error y ofrece abrirlo con otra app solo si es visible; los vídeos del baúl deben restaurarse para usar otro reproductor.

## Privacidad

Sin permiso de Internet, sin cuentas, sin analíticas ni anuncios. Solo se usan permisos de lectura de vídeo, notificaciones, foreground services (mediaPlayback/dataSync), biometría y wake lock.

## Siguientes pasos

1. Compilar y corregir errores de compilación.
2. Instalar en POCO F7 Ultra y validar permisos Android 16.
3. Probar gestos, zoom y conflicto Compose/gestos.
4. Probar baúl con archivos grandes, biometría, PIN, borrado del original y restauración.
5. Matriz de formatos y decodificador software.
6. Medir 4K/HDR, PiP, segundo plano y consumo.
7. Generar keystore de subida, AAB, política de privacidad, Data Safety y prueba interna de Play Store.
8. Completar capturas/feature graphic y publicar.

## Comandos útiles

```powershell
.\scripts\build-debug.ps1
.\scripts\install-debug.ps1
.\scripts\logcat.ps1
.\scripts\build-release.ps1
.\scripts\upload-changes.ps1 -Message "..."
```

Git: rama `main`, remoto `origin` → `https://github.com/jdmontalvo/vidplay`.
