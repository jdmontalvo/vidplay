# Checklist de publicación en Play Store — VidCofre

Paquete: `com.vidplay.app`
Versión: `1.1.0` (`versionCode 2`)
Licencia: GPL-3.0, repositorio público: <https://github.com/jdmontalvo/vidplay>
Anuncios: sí (nativo, máximo 1/hora y 4/día)

## 0. Cuenta

La cuenta anterior está cerrada por inactividad. Hay que crear una **cuenta nueva** (25 $, no reembolsable):

- [ ] Crear la cuenta con la misma identidad y verificar el correo.
- [ ] Verificación de identidad y de dirección (1–3 días).
- [ ] Al ser una cuenta **personal nueva** (posterior al 13/11/2023), asumir la regla de **closed testing con 12 testers durante 14 días** antes de producción.
- [ ] Reclutar 15–16 testers con cuenta Google (margen por si alguien se sale).

## 1. AdMob

- [ ] Crear la cuenta de AdMob con la misma cuenta de Google.
- [ ] Completar perfil de pagos, datos fiscales y verificación por PIN.
- [ ] Registrar la app **VidCofre** con el paquete `com.vidplay.app`.
- [ ] Crear una unidad **Native**.
- [ ] Configurar **Privacy & messaging** (UMP): mensaje EEE/UK/Suiza y mensaje para estados de EE. UU.
- [ ] Limitar la clasificación máxima de anuncios a **G/PG** y bloquear categorías sensibles.
- [ ] Activar el tope de frecuencia **1 impresión por hora** en la unidad (defensa extra).
- [ ] Copiar `ads.properties.example` a `ads.properties` con el App ID y la unidad nativa.

## 2. Antes de subir

- [ ] `.\scripts\build-debug.ps1` y probar en dispositivo real.
- [ ] Probar biblioteca, gestos, baúl, importación, borrado, restauración, biometría y PIN.
- [ ] Confirmar que el anuncio aparece solo en la biblioteca, tras el sexto vídeo, y que no hay anuncios en el reproductor ni en el baúl.
- [ ] Confirmar el tope: segundo intento antes de una hora no carga anuncio.
- [ ] Probar sin conexión: la app funciona y no muestra hueco de anuncio.
- [ ] Revisar `.\scripts\upload-changes.ps1` y que no se suban `keystore.properties`, `ads.properties`, `*.jks` de subida, APK/AAB, vídeos ni la carpeta `personal/`.
- [ ] Respaldar `keystore/vidplay-upload.jks` y `keystore.properties` en un lugar seguro. Sin ellos no se puede actualizar la app.

## 3. Firma y artefacto

```powershell
.\scripts\create-upload-keystore.ps1   # solo si aún no existe
.\scripts\build-release.ps1
```

Salida: `app\build\outputs\bundle\playRelease\app-play-release.aab`.

- [ ] Activar **Play App Signing** en Play Console.
- [ ] Verificar que el AAB lleva `INTERNET`, `ACCESS_NETWORK_STATE` y `AD_ID`.
- [ ] Verificar que la versión personal (`foss`) **no** lleva `INTERNET`.

## 4. Play Console

- [ ] Crear la app con el nombre **VidCofre** y el paquete `com.vidplay.app`.
- [ ] Categoría: Video players & editors.
- [ ] Subir el AAB a **Internal testing** y probar en un dispositivo limpio.
- [ ] Completar **App content**:
  - [ ] Política de privacidad: URL pública (GitHub Pages o similar).
  - [ ] Data safety: ubicación aproximada, interacciones en la app, diagnósticos e identificadores de dispositivo/ID de publicidad; cifrado en tránsito; compartido con terceros; finalidad publicidad/analítica/fraude.
  - [ ] Anuncios: **Sí, contiene anuncios**.
  - [ ] Advertising ID: **Sí**.
  - [ ] Photo and video permissions: función principal (biblioteca y reproductor local), no se sube contenido.
  - [ ] Foreground service types: `mediaPlayback` y `dataSync`.
  - [ ] Contenido y clasificación por edades (IARC; marcar que contiene anuncios).
  - [ ] Público objetivo: 13+ (nunca "Familias": prohíbe app open ads y complica la publicidad).
  - [ ] Acceso a la app: todo disponible sin cuenta.
- [ ] Añadir enlace al **código fuente** (GPL-3.0) en la descripción y en la ficha.
- [ ] Subir icono 512×512, feature graphic 1024×500 y capturas de teléfono.

## 5. Pruebas cerradas

- [ ] Subir el AAB a **Closed testing** con los IDs de prueba de AdMob (los testers no deben generar tráfico inválido).
- [ ] Confirmar 12 testers opt-in durante 14 días seguidos.
- [ ] Responder al formulario de acceso a producción.
- [ ] Cambiar `ads.properties` a los IDs reales y compilar el AAB definitivo de producción.

## 6. Privacidad y seguridad

- [ ] `allowBackup=false` y reglas de extracción excluyen el baúl.
- [ ] `FLAG_SECURE` en el baúl y en la reproducción del baúl.
- [ ] Notificaciones del baúl con título genérico.
- [ ] Sin `MANAGE_EXTERNAL_STORAGE`.
- [ ] Sin FileProvider ni share del baúl.
- [ ] Revisar logs: no imprimir nombres/rutas del baúl.
- [ ] Documentar que desinstalar/borrar datos elimina la clave y el baúl.
- [ ] Documentar que las copias en Google Fotos/nube no se eliminan.

## 7. GPL-3.0

- [ ] `LICENSE` incluido.
- [ ] `NOTICE` y `THIRD_PARTY_LICENSES.md` incluidos.
- [ ] Repositorio público con el código completo y los scripts de compilación.
- [ ] Enlace al código fuente en la app (Ajustes) y en Play Store.
- [ ] Si se modifica NextLib/FFmpeg, publicar los cambios y avisos correspondientes.

## 8. Después de publicar

- [ ] Probar actualización desde la versión anterior.
- [ ] Revisar crash/ANR en Play Console.
- [ ] Vigilar las reseñas sobre publicidad y ajustar el tope si hace falta.
- [ ] Mantener el keystore y las contraseñas en un gestor seguro.
- [ ] Etiquetar la versión en Git (`v1.1.0`).
