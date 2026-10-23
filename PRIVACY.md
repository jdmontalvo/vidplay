# Política de privacidad de VidPlay

Última actualización: 23 de octubre de 2026

VidPlay es un reproductor de vídeo local para Android. **No recopila, transmite ni vende datos personales.** La app no tiene permiso de Internet.

## Qué datos usa

- **Vídeos del dispositivo:** VidPlay lee los vídeos que el usuario selecciona o a los que concede permiso, solo para mostrarlos y reproducirlos.
- **Metadatos locales:** nombres, duración, tamaño, miniaturas y posición de reproducción se guardan en la base de datos privada de la app para mostrar la biblioteca y reanudar.
- **Baúl cifrado:** los vídeos ocultos se cifran con AES-256-GCM y se guardan en el almacenamiento privado de la app. La clave se protege con Android Keystore y un PIN de recuperación.
- **Preferencias:** ajustes de reproducción y del baúl en DataStore, dentro del almacenamiento privado.

## Qué no hace

- No crea cuentas.
- No usa servidores, nube, analíticas, publicidad ni rastreadores.
- No sube vídeos, miniaturas ni metadatos.
- No comparte archivos del baúl.
- No accede a contactos, ubicación, cámara ni micrófono.

## Permisos

- Lectura de vídeos (MediaStore) para la biblioteca.
- Notificaciones para la reproducción y las transferencias del baúl.
- Servicios en primer plano para reproducir y para cifrar/restaurar.
- Biometría/credencial del dispositivo para desbloquear el baúl.
- Wake lock para mantener la reproducción.

El usuario puede revocar los permisos desde Ajustes de Android.

## Baúl y nubes

El cifrado protege los archivos en el almacenamiento de la app. Si un vídeo ya estaba respaldado en Google Fotos u otro servicio antes de ocultarlo, esa copia en la nube no se elimina desde VidPlay. Desinstalar la app o borrar sus datos elimina el baúl y su clave de forma irreversible.

## Menores

VidPlay no está dirigida específicamente a menores y no recopila datos de ninguna persona.

## Cambios

Los cambios de esta política se publicarán en este archivo y en el repositorio.

## Contacto

Abre una incidencia en <https://github.com/jdmontalvo/vidplay/issues>.
