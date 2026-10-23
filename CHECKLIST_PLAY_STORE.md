# Checklist de publicación en Play Store — VidPlay

Paquete: `com.vidplay.app`  
Versión: `1.0.0` (`versionCode 1`)  
Licencia: GPL-3.0, repositorio público: <https://github.com/jdmontalvo/vidplay>

## 1. Antes de subir

- [ ] `.\scripts\build-debug.ps1` y probar en dispositivo real.
- [ ] Probar biblioteca, gestos, baúl, importación, borrado, restauración, biometría y PIN.
- [ ] Confirmar que no hay texto plano del baúl en `filesDir`, caché ni miniaturas.
- [ ] Probar al menos: MP4 H.264, MP4 HEVC, MKV, WebM, AVI, FLV, TS, audio AC3/DTS.
- [ ] Probar 4K/HDR y PiP.
- [ ] Revisar `.\scripts\upload-changes.ps1` y que no se suban `keystore.properties`, `*.jks` de subida, APK/AAB ni vídeos.
- [ ] Respaldar `keystore/vidplay-upload.jks` y `keystore.properties` en un lugar seguro. Sin ellos no se puede actualizar la app.

## 2. Firma y artefacto

```powershell
.\scripts\create-upload-keystore.ps1   # solo si aún no existe
.\scripts\build-release.ps1
```

Salida: `app\build\outputs\bundle\release\app-release.aab`.

El AAB está firmado con la clave de subida. Activa **Play App Signing** en Play Console.

## 3. Play Console

- [ ] Crear la app con el nombre **VidPlay** y el paquete `com.vidplay.app`.
- [ ] Categoría: Video players & editors.
- [ ] Subir el AAB a **Internal testing**.
- [ ] Completar **App content**:
  - [ ] Política de privacidad: `https://github.com/jdmontalvo/vidplay/blob/main/PRIVACY.md`.
  - [ ] Data safety: no se recopilan ni comparten datos; todo se procesa localmente.
  - [ ] Photo and video permissions: declarar que el acceso a vídeos es la función principal (reproducir y gestionar la biblioteca local) y que no se sube contenido.
  - [ ] Foreground service types: `mediaPlayback` y `dataSync` para reproducción y cifrado/restauración del baúl.
  - [ ] Contenido y clasificación por edades.
  - [ ] Público objetivo.
  - [ ] Anuncios: no.
- [ ] Añadir enlace al **código fuente** (GPL-3.0) en la descripción y en la ficha.
- [ ] Subir icono 512×512, feature graphic 1024×500 y capturas de teléfono.
- [ ] Probar la instalación desde Internal testing en un dispositivo limpio.
- [ ] Promover a Closed testing y luego a Production.

## 4. Privacidad y seguridad

- [ ] La app no declara `INTERNET`; verificar en el APK/AAB.
- [ ] `allowBackup=false` y reglas de extracción excluyen el baúl.
- [ ] `FLAG_SECURE` en el baúl y en la reproducción del baúl.
- [ ] Notificaciones del baúl con título genérico.
- [ ] Sin `MANAGE_EXTERNAL_STORAGE`.
- [ ] Sin FileProvider ni share del baúl.
- [ ] Revisar logs: no imprimir nombres/rutas del baúl.
- [ ] Documentar que desinstalar/borrar datos elimina la clave y el baúl.
- [ ] Documentar que las copias en Google Fotos/nube no se eliminan.

## 5. GPL-3.0

- [ ] `LICENSE` incluido.
- [ ] `NOTICE` y `THIRD_PARTY_LICENSES.md` incluidos.
- [ ] Repositorio público con el código completo y los scripts de compilación.
- [ ] Enlace al código fuente en la app (Ajustes) y en Play Store.
- [ ] Si se modifica NextLib/FFmpeg, publicar los cambios y avisos correspondientes.

## 6. Después de publicar

- [ ] Probar actualización desde una versión anterior.
- [ ] Revisar crash/ANR en Play Console.
- [ ] Mantener el keystore y las contraseñas en un gestor seguro.
- [ ] Etiquetar la versión en Git (`v1.0.0`).
