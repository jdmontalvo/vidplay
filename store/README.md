# Assets de la ficha de Play

| Archivo | Uso | Especificación |
|---|---|---|
| `icon-512.png` | Icono de la app en Play | 512×512 px, PNG RGB opaco, < 1 MB |
| `feature-graphic-1024x500.png` | Gráfico destacado | 1024×500 px, PNG/JPEG sin alfa |

## Capturas (pendientes)

Play pide entre 2 y 8 capturas de teléfono, formato 16:9 o 9:16, entre 320 y
3840 px. Recomendado: **1080×1920**, 5 capturas:

1. Biblioteca en cuadrícula (con el anuncio "Patrocinado" visible).
2. Reproductor con controles y gestos.
3. Menú de pistas de audio / subtítulos.
4. Baúl desbloqueado con la lista de vídeos.
5. Ajustes → Privacidad / Opciones de privacidad.

### Cómo hacerlas sin mostrar contenido personal

En el POCO (Android 14+), usa el **acceso parcial**:

1. Ajustes → Apps → VidCofre → Fotos y vídeos.
2. Elige "Seleccionar fotos y vídeos" y marca solo 5–7 vídeos de demo.
3. Abre VidCofre: la biblioteca mostrará únicamente esos.
4. Captura con `adb shell screencap -p /sdcard/cap.png` y `adb pull`, o con los
   botones del teléfono.

Alternativas: Segundo espacio de HyperOS o un emulador limpio.

No publiques capturas con nombres, caras, matrículas o contenido personal. Si
ya las tienes hechas, revisa que no aparezca la barra de estado con datos
personales (notificaciones, operador, etc.).
