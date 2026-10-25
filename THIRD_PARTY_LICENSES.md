# Licencias de terceros

VidCofre se distribuye bajo GNU General Public License v3.0. Este proyecto incluye o enlaza las siguientes bibliotecas:

| Componente | Licencia | Enlace |
|---|---|---|
| AndroidX Media3 / ExoPlayer | Apache License 2.0 | https://github.com/androidx/media |
| NextLib (media3ext, FFmpeg) | GNU GPL v3.0 | https://github.com/anilbeesetti/nextlib |
| FFmpeg (incluido en NextLib) | Según la build de NextLib; consulta sus avisos | https://ffmpeg.org/legal.html |
| Jetpack Compose / Material 3 | Apache License 2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX Room, DataStore, WorkManager, Biometric, Lifecycle, Navigation, Activity | Apache License 2.0 | https://developer.android.com/jetpack/androidx |
| Coil | Apache License 2.0 | https://github.com/coil-kt/coil |
| Kotlin / kotlinx.coroutines | Apache License 2.0 | https://kotlinlang.org/ |
| Material Icons | Apache License 2.0 | https://fonts.google.com/icons |
| Google Mobile Ads SDK (GMA Next-Gen) | Propietario · Términos de las APIs de Google | https://developers.google.com/admob/android/terms |
| User Messaging Platform (UMP) | Propietario · Términos de las APIs de Google | https://developers.google.com/admob/android/privacy |

NextLib es GPL-3.0, por lo que VidCofre en su conjunto se distribuye bajo GPL-3.0. El código fuente completo está en <https://github.com/jdmontalvo/vidplay>.

## Nota sobre el SDK de anuncios

El SDK de Google Mobile Ads es software propietario. La FSF considera que enlazarlo con código GPL-3.0 crea una obra combinada incompatible, aunque en la práctica se distribuye así en numerosas apps y el SDK se ejecuta contra los servicios de Google Play, que son un programa separado.

Para reducir esa tensión, el proyecto mantiene dos flavors:

- `play`: incluye el SDK y es el que se publica en Google Play.
- `foss`: no enlaza el SDK, no declara `INTERNET` y se usa para la compilación personal y para quien quiera recompilar sin publicidad.

El código fuente propio sigue siendo GPL-3.0 y está disponible por completo. Si distribuyes un APK o AAB de VidCofre, debes cumplir la GPL-3.0 y ofrecer el código fuente correspondiente.
