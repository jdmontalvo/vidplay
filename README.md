# VidCofre

Reproductor de vídeo local para Android, rápido y sin nube, con baúl cifrado para ocultar vídeos de la galería y del explorador de archivos.

> El nombre de la app es **VidCofre**. El repositorio y el identificador interno siguen siendo `vidplay` / `com.vidplay.app` para no romper la ruta de actualización.

- **Kotlin + Media3 ExoPlayer 1.11.1** con decodificación hardware y **NextLib FFmpeg** como respaldo software.
- **Compose + Material 3** para biblioteca, baúl y ajustes; reproductor nativo con gestos propios.
- **Zoom 1x–5x con paneo libre**, doble toque para saltar, mantener pulsado para acelerar, brillo/volumen por gestos, PiP, subtítulos, pistas de audio y velocidad 0.25x–4x.
- **Baúl cifrado AES-256-GCM** por bloques de 1 MiB, clave en Android Keystore, PIN de recuperación y descifrado en streaming sin texto plano temporal.
- **Sin cuentas ni analíticas propias.** La versión de Play incluye un único anuncio nativo en la biblioteca, como máximo una vez por hora.
- **GPL-3.0**: el código fuente completo está en <https://github.com/jdmontalvo/vidplay>.

## Requisitos

- Docker Desktop (probado con Docker 29.8.1 en Windows 11).
- `adb` de Android Platform Tools en `C:\Android\platform-tools` (o variable `ADB`).
- Teléfono Android 8.0 (API 26) o superior.

## Compilar

```powershell
.\scripts\build-image.ps1   # solo la primera vez
.\scripts\build-debug.ps1   # flavor "play" con anuncios de prueba
```

El APK queda en:

```text
app\build\outputs\apk\play\debug\app-play-debug.apk
```

## Instalar

```powershell
.\scripts\install-debug.ps1
.\scripts\run-device-tests.ps1   # test de Keystore/PIN (opcional)
```

Si HyperOS bloquea `adb install`:

```powershell
.\scripts\push-apk.ps1
# En el teléfono: Archivos > Descargas > VidCofre-debug.apk > Instalar
```

## Versión personal sin anuncios

```powershell
.\scripts\build-personal.ps1
```

Genera un APK sin SDK de anuncios, sin permiso de Internet y con el paquete `com.vidplay.app.personal` (se instala al lado de la versión de Play, sin compartir baúl). Se copia a `personal\`, una carpeta ignorada por Git: **no se sube al repositorio**.

## Release para Play Store

1. Copia `ads.properties.example` a `ads.properties` y rellena tu App ID y tu unidad nativa de AdMob.
2. Compila:

```powershell
.\scripts\create-upload-keystore.ps1   # solo la primera vez
.\scripts\build-release.ps1            # AAB
.\scripts\build-release.ps1 -Apk       # APK universal de prueba
```

El AAB queda en:

```text
app\build\outputs\bundle\playRelease\app-play-release.aab
```

`keystore.properties`, `ads.properties` y el keystore de subida están ignorados por Git. Guarda una copia segura; sin ellos no podrás actualizar la app ni volver a servir anuncios.

Si `ads.properties` no existe, la build de release compila y firma, pero deja los anuncios desactivados en tiempo de ejecución (`ADS_CONFIGURED=false`) para no enviar tráfico de prueba a tu cuenta.

## Anuncios

- Formato: **nativo** dentro de la pestaña "Vídeos", tras los seis primeros vídeos.
- Nunca en Carpetas, Baúl, Ajustes, búsqueda, reproductor ni imagen en imagen.
- Tope duro: **1 anuncio por hora y 4 al día**, persistido en disco; además, tope espejo en el panel de AdMob.
- Consentimiento UMP (EEE, Reino Unido, Suiza y EE. UU.) con entrada permanente en **Ajustes → Opciones de privacidad**.
- Debug y pruebas internas usan los IDs de prueba de Google. Nunca pulses anuncios reales.

## Baúl

1. Abre **Baúl** y crea un PIN de recuperación (mínimo 8 caracteres).
2. Importa vídeos con el botón **Importar** o desde el menú de un vídeo de la biblioteca.
3. VidCofre cifra el archivo, lo guarda en el almacenamiento privado de la app y solicita borrar el original visible.
4. Para volver a verlo fuera de VidCofre, usa **Hacer visible** y elige `Movies/VidCofre` o una ubicación SAF.

**Avisos importantes:**

- Desinstalar VidCofre o borrar sus datos elimina el baúl y la clave. No hay recuperación sin el PIN/biometría.
- Si el vídeo ya estaba respaldado en Google Fotos u otra nube, ocultarlo localmente no borra esa copia.
- En dispositivos rooteados o con depuración ADB, una persona con acceso privilegiado puede comprometer la app.
- El borrado del original puede fallar en proveedores SAF que no lo permiten; VidCofre avisa y deja el pendiente para reintentar.

## Despliegue Docker

La app se compila dentro de Ubuntu 24.04 con JDK 17, Android SDK 36, build-tools 36.0.0, NDK 27.2.12479018, CMake 3.22.1 y Gradle 8.13. Docker solo se usa para compilar; la app no tiene servidor.

## Documentación

- [AGENTS.md](AGENTS.md): guía técnica completa y estado del proyecto.
- [PLAYSTORE.md](PLAYSTORE.md): textos de ficha, formularios y pasos de publicación.
- [TESTING.md](TESTING.md): hooks de depuración y matriz de pruebas.
- [CHECKLIST_PLAY_STORE.md](CHECKLIST_PLAY_STORE.md): pasos de publicación.
- [PRIVACY.md](PRIVACY.md): política de privacidad.
- [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md): licencias de terceros.
- [LICENSE](LICENSE): GNU GPL v3.0.

## Licencia

VidCofre se distribuye bajo **GNU General Public License v3.0**. Incluye NextLib, que es GPL-3.0. El código fuente está disponible en el repositorio público.
