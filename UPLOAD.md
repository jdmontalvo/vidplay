# Subida a Play Store — runbook

## Artefactos y versiones

| Build | versionCode | App ID AdMob | Unidad nativa | Pista |
|---|---|---|---|---|
| Internal (prueba) | 2 | test de Google | test de Google | Internal testing |
| Closed (testers) | 3 | **real** | test de Google | Closed testing |
| Producción | 4 | **real** | **real** | Production |

El AAB de la pista Internal se compila **sin AdMob** (usa los IDs de prueba de Google) y sirve para validar la firma de Play, la instalación y la pantalla del anuncio. **No lo promociones a producción.**

Los IDs de prueba son:

```text
App ID:  ca-app-pub-3940256099942544~3347511713
Nativa:  ca-app-pub-3940256099942544/2247696110
```

## 1. AdMob (una vez)

1. Cuenta y perfil de pagos completos.
2. App **VidCofre** con paquete `com.jdmontalvo.vidcofre` (aunque no esté publicada).
3. Unidad **Nativa** → copiar App ID y unidad.
4. **Privacidad y mensajería**: mensaje EEE/UK/Suiza publicado + mensaje estados EE. UU.
5. Unidad → avanzado: **1 impresión/hora**, clasificación **G/PG**, categorías sensibles bloqueadas.
6. Anotar `pub-…` para `app-ads.txt`.

## 2. Builds

Con `ads.properties` (ignorado por Git):

```properties
# Internal testing (IDs de prueba)
admobAppId=ca-app-pub-3940256099942544~3347511713
nativeUnitId=ca-app-pub-3940256099942544/2247696110
```

```powershell
.\scripts\build-release.ps1                 # Internal testing (versionCode 2)
.\scripts\build-release.ps1 -VersionCode 3  # Closed testing
.\scripts\build-release.ps1 -VersionCode 4  # Producción
# -> app\build\outputs\bundle\playRelease\app-play-release.aab
```

`-VersionCode` evita editar el archivo y garantiza que Play reciba un código creciente.

El script avisa si detecta IDs de prueba, `adTestMode` o una posición de anuncio distinta de 6.

## 3. Play Console

1. **Crear app**: VidCofre, `com.jdmontalvo.vidcofre`, es-ES, gratis, app, no juego.
2. **Internal testing** → subir el AAB → añadir tu correo como tester → instalar desde el enlace de Play en un dispositivo limpio.
3. **App content** (respuestas en `PLAYSTORE.md`):
   - Política de privacidad: `https://jdmontalvo.github.io/vidplay/privacy.html`.
   - Anuncios: sí · Advertising ID: sí.
   - Data safety: ubicación aproximada, interacciones, diagnósticos, identificadores.
   - Permisos de foto/vídeo: función principal + vídeo si lo piden.
   - Foreground services: `mediaPlayback` y `dataSync`.
   - Clasificación IARC, público 13+, acceso sin restricciones.
   - Exportación: la app usa cifrado (exención mass-market).
4. **Play App Signing**: aceptar al subir.
5. **Store listing**: textos de `PLAYSTORE.md`, icono `store/icon-512.png`, feature `store/feature-graphic-1024x500.png`, capturas (ver `store/README.md`).
6. **Closed testing**: crear pista, subir el AAB con el App ID real, añadir 15–16 emails.
7. Los testers deben **optar, instalar y quedarse 14 días seguidos** (mínimo 12 activos).
8. Tras 14 días: **Apply for production access** → cuestionario → revisión (1–7 días).
9. **Production**: AAB con IDs reales y `versionCode` superior, rollout 20 % → 100 %.

## 4. Privacidad web

1. `docs/` ya contiene `index.html`, `privacy.html` y `.nojekyll`.
2. GitHub → Settings → Pages → Source: rama `main`, carpeta `/docs`.
3. URL resultante: `https://jdmontalvo.github.io/vidplay/`.
4. `app-ads.txt` (opcional) debe ir en la raíz del dominio: repo `jdmontalvo.github.io` con:

```text
google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0
```

## 5. Después de publicar

- Vigilar crash/ANR y reseñas sobre anuncios.
- Revisar impresiones/eCPM en AdMob; ajustar el tope si molesta.
- Etiquetar `v1.1.0` en Git cuando la build de producción esté validada.
- No desinstalar la app con el baúl lleno sin haberlo respaldado: borrar datos elimina la clave.

## 6. Pendiente antes de producción

- [ ] Validar en dispositivo la tarjeta nativa con IDs de prueba (bloqueado: la depuración inalámbrica se desconectó).
- [ ] Crear AdMob y rellenar `ads.properties` real.
- [ ] Repetir la build con `versionCode 3` para Closed testing.
- [ ] Capturas de la ficha sin datos personales.
- [ ] Reclutar 12+ testers.
