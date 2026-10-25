# AGENTS.md — VidCofre (repo `vidplay`)

Guía de continuación del proyecto. Léela completa antes de cambiar código.

## Qué es

**VidCofre** es un reproductor de vídeo local para Android con un baúl cifrado y un anuncio nativo discreto en la biblioteca.

- Nombre visible: VidCofre (el repo y el paquete siguen siendo `vidplay` / `com.vidplay.app`)
- Application ID: `com.vidplay.app` (Play) · `com.vidplay.app.personal` (build personal)
- Versión: `1.1.0` (`versionCode 2`)
- Repositorio: <https://github.com/jdmontalvo/vidplay> (público, GPL-3.0)
- SDK: compile/target 36, min 26
- Probado en: POCO F7 Ultra (Android 16, HyperOS 3), dispositivo `fc914e16`

## Flavors

| Flavor | Paquete | Anuncios | INTERNET | Uso |
|---|---|---|---|---|
| `play` | `com.vidplay.app` | Sí (AdMob nativo) | Sí | Google Play |
| `foss` | `com.vidplay.app.personal` | No (SDK no enlazado) | No | Uso personal / recompilar sin ads |

El código compartido está en `app/src/main`. Cada flavor aporta su `AdsProvider`:

- `app/src/play/java/com/vidplay/app/ads/` → `PlayAdsController` (AdMob + UMP).
- `app/src/foss/java/com/vidplay/app/ads/` → `NoAdsController` (no-op, sin SDK).

El manifest `src/play/AndroidManifest.xml` añade `INTERNET`, `ACCESS_NETWORK_STATE`, `AD_ID`, el App ID de AdMob y restaura `RECEIVE_BOOT_COMPLETED` (el SDK de anuncios lo elimina y WorkManager lo necesita). El manifest `src/foss/AndroidManifest.xml` fuerza la retirada de esos permisos.

## Estado

Implementado en código:

- Biblioteca MediaStore con permisos modernos, acceso parcial Android 14, búsqueda, ordenación, carpetas, miniaturas Coil y apertura SAF.
- Reproductor Media3 + NextLib, PiP, notificación multimedia, subtítulos, pistas de audio, velocidad, relación de aspecto, rotación, temporizador, brillo/volumen, reanudación.
- Gestos: un toque, doble toque izquierda/centro/derecha, mantener pulsado para acelerar, pellizco 1x–5x, paneo libre, deslizamiento para buscar/brillo/volumen, bloqueo.
- Baúl: AES-256-GCM por bloques, DEK envuelta por Keystore y PIN, miniaturas cifradas, `VaultDataSource` para Media3, importación con WorkManager en primer plano, borrado del original con diálogo MediaStore/SAF, restauración con verificación SHA-256.
- Bloqueo con biometría/credencial, PIN de recuperación, auto-bloqueo en segundo plano, `FLAG_SECURE`, títulos genéricos en notificaciones del baúl, PiP del baúl desactivado por defecto.
- `PlayerActivity` acepta playlists internas solo con un token aleatorio por proceso; los intents externos `ACTION_VIEW` solo pueden abrir `content://`/`file://` y nunca `vault://`.
- WorkManager usa `SystemForegroundService` con `foregroundServiceType="dataSync"` fusionado en el manifiesto.
- Publicidad (flavor `play`): tarjeta nativa en la biblioteca tras los 6 primeros vídeos, tope de 1/hora y 4/día persistido, sin anuncios en reproductor/baúl/carpetas/ajustes/búsqueda, consentimiento UMP con "Opciones de privacidad" en Ajustes.
- Build personal `foss` sin SDK de anuncios ni permiso de Internet.
- Docker, scripts PowerShell, keystore de depuración fijo, build release/AAB, GPL-3.0 y documentación.

Pendiente de validar en dispositivo:

- Que la tarjeta nativa infle correctamente (`NativeAdView` + `MediaView`) y que el SDK registre impresión al primer píxel visible.
- Que el tope de 1/hora y 4/día se respete al reabrir la biblioteca.
- Que el formulario UMP aparezca en EEE/UK/CH y que "Opciones de privacidad" lo reabra.
- Que sin conexión no quede hueco ni error visible.
- Que la build `foss` no tenga permiso `INTERNET` en el APK.
- Importación del baúl con el arreglo de desbloqueo en segundo plano, borrado del original y restauración.
- Biometría, PIN y Keystore con bloqueo/desbloqueo real.
- Gestos y conflicto entre la capa Compose de controles y la vista nativa.
- Matriz de formatos (HEVC, AV1, MKV, AVI, FLV, TS, audio AC3/DTS).
- 4K/HDR, PiP, segundo plano prolongado y consumo.

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
| GMA Next-Gen | 1.6.0 |
| UMP | 4.0.0 |
| Compose BOM | 2026.05.01 |
| Room | 2.8.5 |
| DataStore | 1.3.0 |
| Coil | 3.6.3 |
| WorkManager | 2.12.0 |
| Biometric | 1.4.0-alpha07 |
| ABIs | arm64-v8a, armeabi-v7a |

## Compilar e instalar

```powershell
.\scripts\build-image.ps1
.\scripts\build-debug.ps1          # playDebug, anuncios de prueba
.\scripts\install-debug.ps1
.\scripts\run-device-tests.ps1     # test instrumentado de Keystore/PIN
.\scripts\build-personal.ps1       # APK personal sin anuncios en .\personal\
```

Salida debug: `app\build\outputs\apk\play\debug\app-play-debug.apk`.

La carpeta `personal\` está ignorada por Git. No publicar nunca ese APK ni una build `foss` sin publicidad.

Release:

```powershell
.\scripts\create-upload-keystore.ps1
.\scripts\build-release.ps1          # AAB firmado (playRelease)
.\scripts\build-release.ps1 -Apk     # APK universal de prueba
```

`keystore.properties`, `ads.properties`, `*.jks` (salvo `keystore/vidplay-debug.jks`), APK, AAB, vídeos, WAV, PCM y archivos del baúl están fuera de Git.

`ads.properties` (plantilla en `ads.properties.example`) aporta `admobAppId` y `nativeUnitId`. Si falta, la release compila con `ADS_CONFIGURED=false` y no pide anuncios.

## Estructura

```text
app/src/main/java/com/vidplay/app/
  MainActivity.kt              # navegación Compose (Vídeos, Carpetas, Baúl, Ajustes)
  VidPlayApp.kt                # contenedor, canales, Coil, auto-bloqueo, AdsController
  ads/AdsController.kt         # interfaz común de publicidad
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
    library/                   # biblioteca, ViewModel y hueco del anuncio nativo
    folders/                   # vista de carpetas
    vault/                     # baúl, biometría, PIN, import/export
    settings/                  # ajustes, privacidad y opciones de anuncios
    components/                # tarjetas, miniaturas, estados vacíos, VideoGrid/List
    theme/

app/src/play/                  # AdsProvider, PlayAdsController, layout ad_native_card.xml
app/src/foss/                  # AdsProvider no-op, strings "VidCofre Personal"
```

## Baúl: formato y seguridad

- Ruta: `filesDir/vault/<uuid>.vpv` (almacenamiento interno privado).
- Miniaturas: `<uuid>.vpt`, cifradas, cargadas solo en memoria.
- Cabecera de 56 bytes: magic `VPVAULT1`, versión, flags, tamaño de bloque, tamaño plano y SHA-256.
- Bloques de 1 MiB: `IV aleatorio (12 B) + ciphertext + tag GCM (16 B)`.
- Clave de datos (DEK) aleatoria de 256 bits. Se guarda envuelta por:
  - una clave AES del Android Keystore (`.dek`);
  - una clave derivada del PIN con PBKDF2-HMAC-SHA256 (210 000 iteraciones, `.pin`).
- El PIN es el camino de recuperación. La biometría es la puerta de acceso de la app; la clave de Keystore no está ligada a autenticación.
- `VaultDataSource` descifra el bloque solicitado y permite seek. Nunca se escribe texto plano en disco.
- La exportación verifica el SHA-256 y solo entonces borra la copia cifrada.
- `allowBackup=false`, `dataExtractionRules` excluye todo, `FLAG_SECURE` en el baúl y en la reproducción del baúl, notificaciones con título genérico.
- No hay FileProvider, share ni rutas expuestas del baúl.

**Riesgos aceptados:** root/ADB, pérdida de datos al desinstalar/borrar datos, copias previas en nubes, proveedores SAF que no permiten borrar el original, y que la puerta biométrica es a nivel de app.

## Reproductor

- `PlaybackService` construye `ExoPlayer` con `NextRenderersFactory` y `EXTENSION_RENDERER_MODE_ON`.
- `DefaultDataSource.Factory` usa `VaultDataSource.Factory` como base para el esquema `vault://`.
- `PlayerActivity` conecta un `MediaController`; las peticiones llegan por extras o por `ACTION_VIEW`.
- El título real del baúl se muestra en la UI, pero el `MediaMetadata` usa "Vídeo protegido" cuando `hideVaultNotification` está activo.
- `ZoomablePlayerLayout` transforma el `PlayerView` (TextureView) con escala 1–5 y traslación limitada.

## Publicidad

- Solo flavor `play`. El controller vive en `app/src/play` y se crea desde `AppContainer`.
- Un único anuncio nativo en la pestaña "Vídeos", tras el sexto vídeo, con etiqueta "Patrocinado" y "Ocultar anuncio".
- Tope en `SharedPreferences` (`vidplay_ads`): 3 600 000 ms entre cargas y 4 cargas/día. Además, tope de 1/hora en el panel de AdMob.
- Sin anuncios en Carpetas, Baúl, Ajustes, búsqueda, reproductor ni PiP. No se pide anuncio si no hay permiso de vídeo, si la biblioteca está vacía o si el usuario lo ocultó en la sesión.
- Consentimiento con UMP en cada arranque; el formulario se muestra al abrir la biblioteca. `privacyOptionsRequired` habilita la entrada en Ajustes.
- Debug y pruebas cerradas usan IDs de prueba de Google. Nunca pulsar anuncios reales.
- `proguard-rules.pro` mantiene el SDK y UMP.

## Privacidad

- La versión de Play declara `INTERNET`, `ACCESS_NETWORK_STATE` y `AD_ID`; la personal no declara ninguno.
- Los vídeos, miniaturas, nombres, rutas y el baúl nunca se envían a ningún servidor.
- `PRIVACY.md` describe qué recoge AdMob y cómo retirar el consentimiento.
- Data Safety de Play debe declarar ubicación aproximada, interacciones, diagnósticos e identificadores de dispositivo.
- Sin analíticas propias, sin cuentas, sin anuncios fuera de la biblioteca.

## Formatos

Media3 aporta extractores para MP4/M4V/MOV, MKV, WebM, AVI, FLV, TS/M2TS, MPEG/MPG, 3GP, OGG. NextLib añade decodificación software de H.264, HEVC, VP8, VP9, AV1 y audio AC3/EAC3/DTS/TrueHD/ALAC/AMR.

No se garantizan todos los códecs legacy en todos los dispositivos. Si un archivo falla, la UI muestra error y ofrece abrirlo con otra app solo si es visible; los vídeos del baúl deben restaurarse.

## Siguientes pasos

1. Probar la tarjeta nativa y el consentimiento en dispositivo real.
2. Crear la cuenta nueva de Play (la anterior está cerrada) y verificar identidad.
3. Crear AdMob, unidad nativa, mensajes UMP y `ads.properties`.
4. Generar assets de ficha (icono 512, feature graphic, capturas).
5. Publicar privacidad en GitHub Pages y `app-ads.txt`.
6. Closed testing con 12+ testers durante 14 días.
7. Producción con rollout gradual y seguimiento de reseñas/crashes.
8. Valorar "quitar anuncios" (IAP) o ajustar el tope según datos reales.

## Comandos útiles

```powershell
.\scripts\build-debug.ps1
.\scripts\install-debug.ps1
.\scripts\logcat.ps1
.\scripts\build-release.ps1
.\scripts\build-personal.ps1
.\scripts\upload-changes.ps1 -Message "..."
```

Git: rama `main`, remoto `origin` → `https://github.com/jdmontalvo/vidplay`.
