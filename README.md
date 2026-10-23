# VidPlay

Reproductor de vídeo local para Android, rápido y sin nube, con baúl cifrado para ocultar vídeos de la galería y del explorador de archivos.

- **Kotlin + Media3 ExoPlayer 1.11.1** con decodificación hardware y **NextLib FFmpeg** como respaldo software.
- **Compose + Material 3** para biblioteca, baúl y ajustes; reproductor nativo con gestos propios.
- **Zoom 1x–5x con paneo libre**, doble toque para saltar, mantener pulsado para acelerar, brillo/volumen por gestos, PiP, subtítulos, pistas de audio y velocidad 0.25x–4x.
- **Baúl cifrado AES-256-GCM** por bloques de 1 MiB, clave en Android Keystore, PIN de recuperación y descifrado en streaming sin texto plano temporal.
- **Sin internet, sin cuentas, sin analíticas y sin anuncios.**
- **GPL-3.0**: el código fuente completo está en <https://github.com/jdmontalvo/vidplay>.

## Requisitos

- Docker Desktop (probado con Docker 29.8.1 en Windows 11).
- `adb` de Android Platform Tools en `C:\Android\platform-tools` (o variable `ADB`).
- Teléfono Android 8.0 (API 26) o superior.

## Compilar

```powershell
.\scripts\build-image.ps1   # solo la primera vez
.\scripts\build-debug.ps1
```

El APK queda en:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Instalar

```powershell
.\scripts\install-debug.ps1
```

Si HyperOS bloquea `adb install`:

```powershell
.\scripts\push-apk.ps1
# En el teléfono: Archivos > Descargas > VidPlay-debug.apk > Instalar
```

## Release para Play Store

```powershell
.\scripts\create-upload-keystore.ps1
.\scripts\build-release.ps1
```

El AAB queda en:

```text
app\build\outputs\bundle\release\app-release.aab
```

`keystore.properties` y el keystore de subida están ignorados por Git. Guarda una copia segura de ambos; sin ellos no podrás actualizar la app.

## Baúl

1. Abre **Baúl** y crea un PIN de recuperación (mínimo 8 caracteres).
2. Importa vídeos con el botón **Importar** o desde el menú de un vídeo de la biblioteca.
3. VidPlay cifra el archivo, lo guarda en el almacenamiento privado de la app y solicita borrar el original visible.
4. Para volver a verlo fuera de VidPlay, usa **Hacer visible** y elige `Movies/VidPlay` o una ubicación SAF.

**Avisos importantes:**

- Desinstalar VidPlay o borrar sus datos elimina el baúl y la clave. No hay recuperación sin el PIN/biometría.
- Si el vídeo ya estaba respaldado en Google Fotos u otra nube, ocultarlo localmente no borra esa copia.
- En dispositivos rooteados o con depuración ADB, una persona con acceso privilegiado puede comprometer la app.
- El borrado del original puede fallar en proveedores SAF que no lo permiten; VidPlay avisa y deja el pendiente para reintentar.

## Despliegue Docker

La app se compila dentro de Ubuntu 24.04 con JDK 17, Android SDK 36, build-tools 36.0.0, NDK 27.2.12479018, CMake 3.22.1 y Gradle 8.13. Docker solo se usa para compilar; la app no tiene servidor.

## Documentación

- [AGENTS.md](AGENTS.md): guía técnica completa y estado del proyecto.
- [TESTING.md](TESTING.md): hooks de depuración y matriz de pruebas.
- [CHECKLIST_PLAY_STORE.md](CHECKLIST_PLAY_STORE.md): pasos de publicación.
- [PRIVACY.md](PRIVACY.md): política de privacidad.
- [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md): licencias de terceros.
- [LICENSE](LICENSE): GNU GPL v3.0.

## Licencia

VidPlay se distribuye bajo **GNU General Public License v3.0**. Incluye NextLib, que es GPL-3.0. El código fuente está disponible en el repositorio público.
