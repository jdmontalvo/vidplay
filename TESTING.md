# Guía de pruebas — VidPlay

## Compilar e instalar

```powershell
.\scripts\build-image.ps1
.\scripts\build-debug.ps1
.\scripts\install-debug.ps1
```

El POCO F7 Ultra de pruebas tiene `adb` en `C:\Android\platform-tools\adb.exe`. En HyperOS, `adb shell input tap` está bloqueado (`INJECT_EVENTS`); usa toques manuales o los hooks de depuración.

## Hooks de depuración

Solo funcionan en builds debug (`BuildConfig.DEBUG`). Sirven para preparar el baúl sin depender de la inyección de eventos.

### Crear/desbloquear el baúl e importar un vídeo de MediaStore

Sustituye `<ID>` por un `_id` de `content://media/external/video/media`.

```powershell
adb shell am start -n com.vidplay.app/.MainActivity `
  -d content://media/external/video/media/<ID> `
  --grant-read-uri-permission `
  --es debug_vault_pin 12345678 `
  --ez debug_delete_original false
```

- `debug_delete_original false` evita borrar el vídeo original durante la prueba.
- Si el baúl no existe, el hook lo crea con el PIN indicado. Si existe, lo desbloquea.
- La importación se ejecuta con WorkManager. El worker desenvuelve la DEK del Keystore aunque el proceso se haya reiniciado.

### Reproducir el último vídeo del baúl

```powershell
adb shell am start -n com.vidplay.app/.MainActivity `
  --es debug_vault_pin 12345678 `
  --ez debug_play_latest_vault true
```

### Inspeccionar el almacenamiento del baúl

```powershell
adb shell run-as com.vidplay.app ls -la files/vault
```

Debe mostrar `.dek`, `.pin` y archivos `<uuid>.vpv` / `<uuid>.vpt`. Los `.vpv` no deben ser reproducibles por la galería ni por un explorador normal.

## Pruebas manuales imprescindibles

### Reproductor

- [ ] Abrir un vídeo desde la biblioteca.
- [ ] Un toque muestra/oculta controles.
- [ ] Doble toque izquierda/derecha salta ±10 s (configurable).
- [ ] Doble toque centro play/pausa.
- [ ] Mantener pulsado acelera; al soltar restaura la velocidad.
- [ ] Pellizcar hace zoom 1x–5x; arrastrar mueve la imagen; el botón de reset vuelve a 1x.
- [ ] Deslizar horizontal busca; vertical izquierda/derecha cambia brillo/volumen.
- [ ] Bloqueo de controles y desbloqueo.
- [ ] Velocidad 0.25x–4x.
- [ ] Subtítulos embebidos y archivo externo .srt/.vtt/.ass.
- [ ] Cambio de pista de audio y de relación de aspecto.
- [ ] Rotación, PiP, temporizador y reproducción en segundo plano.
- [ ] Siguiente/anterior en una carpeta.
- [ ] Reanudar tras cerrar y volver a abrir.
- [ ] Error controlado con un archivo no soportado y opción de abrir con otra app (solo visible).

### Biblioteca

- [ ] Permiso completo Android 13/14.
- [ ] Permiso parcial Android 14 (seleccionar más).
- [ ] Búsqueda, orden y vista lista/cuadrícula.
- [ ] Carpetas y apertura de un vídeo de una carpeta.
- [ ] Abrir un archivo con SAF.
- [ ] Ocultar un vídeo desde la biblioteca: debe ir al baúl y pedir borrar el original.

### Baúl

- [ ] Crear el baúl con PIN; confirmar el PIN.
- [ ] Desbloquear con biometría y con PIN.
- [ ] Importar uno o varios vídeos; ver progreso y notificación.
- [ ] El original desaparece de la galería/explorador; si no se puede borrar, aparece el aviso de pendiente.
- [ ] Confirmar el borrado del original desde el diálogo del sistema.
- [ ] Reproducir desde el baúl, hacer seek y comprobar que no hay texto plano en `filesDir`.
- [ ] Restaurar en `Movies/VidPlay` y verlo en la galería.
- [ ] Restaurar con “Elegir ubicación” (SAF).
- [ ] Eliminar un vídeo del baúl.
- [ ] Cancelar una importación y verificar que no queda un `.part`.
- [ ] Comprobar `FLAG_SECURE`: captura de pantalla bloqueada en el baúl y al reproducir contenido del baúl.
- [ ] Comprobar que la notificación del baúl no muestra el nombre original.
- [ ] Desinstalar/borrar datos: el baúl se pierde (esperado).

### Formatos

Prepara archivos de prueba con ffmpeg en otra máquina o descarga samples:

```bash
ffmpeg -i input.mp4 -c:v libx265 -crf 28 -c:a copy hevc.mp4
ffmpeg -i input.mp4 -c:v libvpx-vp9 -b:v 1M -c:a libopus webm.webm
ffmpeg -i input.mp4 -c copy sample.mkv
ffmpeg -i input.mp4 -c:v mpeg4 -c:a libmp3lame xvid.avi
ffmpeg -i input.mp4 -c copy sample.ts
```

- [ ] MP4 H.264 + AAC (hardware).
- [ ] MP4 HEVC/H.265.
- [ ] WebM VP9 + Opus.
- [ ] MKV con varias pistas de audio/subtítulos.
- [ ] AVI (MPEG-4/Xvid).
- [ ] FLV H.264.
- [ ] TS/M2TS.
- [ ] AV1 (hardware o dav1d de NextLib).
- [ ] AC3/EAC3/DTS en MKV (fallback NextLib).
- [ ] 4K/HDR y PiP.
- [ ] archivo no soportado: error claro.

### Release

```powershell
.\scripts\build-release.ps1
.\scripts\build-release.ps1 -Apk
```

- [ ] Instalar `app-release.apk` en un dispositivo limpio (desinstalar debug antes).
- [ ] Verificar que el AAB está firmado y que R8 no rompe workers ni JNI.
- [ ] `zipalign -c -P 16 -v 4 app-release.apk`.
- [ ] Probar la app release con un archivo del baúl.
- [ ] Subir el AAB a Internal testing.

## Comandos útiles

```powershell
.\scripts\logcat.ps1
adb shell run-as com.vidplay.app ls -la files/vault
adb shell run-as com.vidplay.app ls -la databases
adb shell dumpsys media_session | Select-String com.vidplay
```

## Notas

- El PIN de prueba `12345678` solo se usa en builds debug. Cámbialo en la app para uso real.
- No subas APK/AAB/vídeos al repositorio.
- El baúl no se puede recuperar si se borran los datos de la app o se desinstala.
