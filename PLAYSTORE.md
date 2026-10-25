# Ficha de Play Store — VidCofre

## Identidad

| Campo | Valor |
|---|---|
| Nombre (máx. 30) | `VidCofre — vídeos y baúl` |
| Paquete | `com.vidplay.app` |
| Categoría | Video players & editors |
| Etiquetas | video player, offline, privacidad, reproductor |
| Precio | Gratis (con anuncios) |
| Email de contacto | davidmontalvo59@gmail.com |
| Web | `https://github.com/jdmontalvo/vidplay` |
| Privacidad | URL pública de `PRIVACY.md` (GitHub Pages) |

## Textos (ES)

**Descripción corta (máx. 80):**

> Reproductor local con baúl cifrado. Tus vídeos, privados y sin nube.

**Descripción larga:**

```text
VidCofre es un reproductor de vídeo local, rápido y sin nube, con un baúl cifrado para los vídeos que no quieres dejar a la vista.

REPRODUCTOR
• Reproduce MP4, MKV, WebM, AVI, FLV, TS y más, con decodificación hardware y respaldo FFmpeg.
• Zoom de 1x a 5x con paneo, doble toque para retroceder o avanzar, mantén pulsado para acelerar.
• Brillo y volumen con gestos, subtítulos, pistas de audio, velocidad 0.25x–4x, temporizador, rotación e imagen en imagen.
• Reanuda donde lo dejaste y sigue reproduciendo en segundo plano.

BAÚL CIFRADO
• Oculta vídeos de la galería y del explorador de archivos.
• Cifrado AES-256-GCM por bloques; la clave vive en Android Keystore y tienes un PIN de recuperación.
• Importa, restaura y borra el original con verificación SHA-256.
• Nada del baúl se sube a internet: ni vídeos, ni miniaturas, ni nombres.

PRIVACIDAD
• Sin cuentas y sin analíticas propias.
• Tus vídeos nunca salen del teléfono.
• La versión de Play muestra un único anuncio nativo en la biblioteca, como máximo una vez por hora, para mantener el desarrollo. Puedes cambiar tu consentimiento en Ajustes → Opciones de privacidad.

CÓDIGO ABIERTO
• VidCofre es software libre bajo GPL-3.0. El código fuente completo está en github.com/jdmontalvo/vidplay.

AVISO
Desinstalar la app o borrar sus datos elimina el baúl y su clave de forma irreversible. Si un vídeo ya estaba en Google Fotos u otra nube, ocultarlo aquí no borra esa copia.
```

## Textos (EN)

**Short description:**

> Local video player with an encrypted vault. Your videos, private and offline.

**Full description:**

```text
VidCofre is a fast, offline local video player with an encrypted vault for the videos you want out of sight.

PLAYER
• Plays MP4, MKV, WebM, AVI, FLV, TS and more with hardware decoding and FFmpeg fallback.
• 1x–5x zoom with panning, double-tap to seek, hold to speed up.
• Gesture brightness and volume, subtitles, audio tracks, 0.25x–4x speed, sleep timer, rotation and picture-in-picture.
• Resume where you left off and keep playing in the background.

ENCRYPTED VAULT
• Hide videos from the gallery and file manager.
• AES-256-GCM block encryption; the key lives in Android Keystore plus a recovery PIN.
• Import, restore and delete the original with SHA-256 verification.
• Nothing from the vault ever leaves the device: no videos, thumbnails or names.

PRIVACY
• No accounts and no first-party analytics.
• Your videos never leave your phone.
• The Play version shows one native ad in the library, at most once per hour, to fund development. You can change your consent in Settings → Privacy options.

OPEN SOURCE
• VidCofre is free software under GPL-3.0. Full source at github.com/jdmontalvo/vidplay.
```

## Data safety (respuestas)

| Pregunta | Respuesta |
|---|---|
| ¿Recopila o comparte datos? | Sí |
| Ubicación aproximada | Recopilada y compartida · Publicidad · No opcional |
| Interacciones en la app | Recopiladas y compartidas · Publicidad, analítica, prevención de fraude |
| Diagnósticos | Recopilados y compartidos · Analítica, prevención de fraude |
| Identificadores de dispositivo u otros | Recopilados y compartidos (ID de publicidad, App Set ID) · Publicidad, analítica, fraude |
| ¿Cifrados en tránsito? | Sí |
| ¿El usuario puede pedir borrado? | No hay cuentas; los datos locales se borran desinstalando. Los de publicidad, desde Google |
| ¿Datos procesados de forma efímera? | No |

Fuente: <https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure>

## App content

- **Anuncios:** sí, contiene anuncios.
- **Advertising ID:** sí.
- **Acceso a la app:** sin restricciones (el PIN del baúl es local).
- **Público objetivo:** 13+.
- **Clasificación:** cuestionario IARC; marcar "contiene anuncios". Un reproductor local sin contenido propio suele quedar en "Todos".
- **Permisos de foto y vídeo:** la app es un reproductor y gestor de biblioteca local; el acceso es la función principal y no se sube contenido.
- **`READ_BASIC_PHONE_STATE`:** permiso normal que declara el SDK de anuncios de Google; no se pide al usuario ni se usa para llamadas.
- **Servicios en primer plano:** `mediaPlayback` (reproducción) y `dataSync` (cifrado/restauración del baúl con WorkManager).
- **Gobierno, noticias, salud, finanzas:** no.

## Capturas necesarias

- Icono 512×512 PNG (sin transparencia).
- Feature graphic 1024×500.
- 4–6 capturas 1080×1920: biblioteca, reproductor con gestos, menú de pistas, baúl desbloqueado, importación, ajustes.
- Opcional: vídeo de 30 s.

## AdMob y `app-ads.txt`

1. Publica `PRIVACY.md` como página web (GitHub Pages) y usa esa URL en la ficha.
2. Publica en la raíz del mismo dominio:

```text
google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0
```

3. Usa el App ID `ca-app-pub-…~…` en `ads.properties` (`admobAppId`) y la unidad nativa en `nativeUnitId`.

## Notas para revisión y testers

- En closed testing se compila con **IDs de prueba** de AdMob; los testers no deben pulsar anuncios reales.
- El anuncio aparece solo en la pestaña "Vídeos", después de seis vídeos, con etiqueta "Patrocinado" y opción "Ocultar anuncio".
- No hay anuncios en el reproductor, el baúl, Carpetas ni Ajustes.
- La versión personal (`com.vidplay.app.personal`) no se publica y no debe subirse a Play.

## Riesgos de revisión

- **Nombre:** existen otras apps "VidPlay" en Play; por eso la ficha usa **VidCofre**. No reutilizar el nombre antiguo en la ficha.
- **Baúl:** describirlo como privacidad personal, nunca como herramienta para ocultar contenido a terceros o a controles parentales.
- **Anuncios:** mantener la etiqueta "Patrocinado" visible y no colocar el anuncio como primera tarjeta.
- **GPL:** incluir el enlace al código fuente en la ficha y en la app.
