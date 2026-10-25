# Política de privacidad de VidCofre

Última actualización: 24 de octubre de 2026

VidCofre es un reproductor de vídeo local para Android con un baúl cifrado. **Los vídeos, las miniaturas y el contenido del baúl nunca salen del dispositivo.** La versión publicada en Google Play muestra publicidad; la versión personal (paquete `com.vidplay.app.personal`) no incluye anuncios ni permiso de Internet.

## Qué datos usa la app

- **Vídeos del dispositivo:** VidCofre lee los vídeos a los que el usuario concede permiso, solo para mostrarlos y reproducirlos.
- **Metadatos locales:** nombres, duración, tamaño, miniaturas y posición de reproducción se guardan en la base de datos privada de la app para mostrar la biblioteca y reanudar.
- **Baúl cifrado:** los vídeos ocultos se cifran con AES-256-GCM y se guardan en el almacenamiento privado de la app. La clave se protege con Android Keystore y un PIN de recuperación.
- **Preferencias:** ajustes de reproducción y del baúl en DataStore, dentro del almacenamiento privado.
- **Anuncios:** la versión de Play usa Google AdMob para mostrar un único anuncio nativo dentro de la biblioteca, como máximo una vez por hora.

## Publicidad (solo versión de Play)

VidCofre usa el SDK de Google Mobile Ads y la Plataforma de Mensajes al Usuario (UMP) de Google. Antes de cargar el primer anuncio se pide el consentimiento cuando la ley lo exige (Espacio Económico Europeo, Reino Unido, Suiza y varios estados de EE. UU.).

Según la documentación de Google, el SDK de anuncios puede recoger y compartir automáticamente:

- **Dirección IP**, que puede usarse para estimar la ubicación aproximada.
- **Interacciones con la app**, como aperturas, pulsaciones y visualizaciones de vídeo.
- **Información de diagnóstico**, como tiempos de inicio, bloqueos y consumo de energía.
- **Identificadores de dispositivo y cuenta**, incluidos el ID de publicidad de Android y el App Set ID.

Estos datos se usan para publicidad, analítica y prevención de fraude, se cifran en tránsito con TLS y se comparten con Google y sus socios publicitarios. VidCofre **no** envía a Google el nombre de tus vídeos, sus rutas, sus miniaturas ni ningún dato del baúl.

Puedes cambiar o retirar el consentimiento en **Ajustes → Opciones de privacidad**, y consultar:

- Privacidad de Google: <https://policies.google.com/privacy>
- Publicidad de Google: <https://policies.google.com/technologies/ads>
- Cómo funciona AdMob: <https://support.google.com/admob/answer/6128543>

También puedes restablecer o eliminar el ID de publicidad desde los ajustes de Android.

## Qué no hace

- No crea cuentas.
- No sube vídeos, miniaturas ni metadatos a ningún servidor.
- No comparte archivos del baúl.
- No accede a contactos, ubicación precisa, cámara ni micrófono.
- No usa analíticas propias ni rastreadores adicionales fuera del SDK de anuncios.

## Permisos

- Lectura de vídeos (MediaStore) para la biblioteca.
- Notificaciones para la reproducción y las transferencias del baúl.
- Servicios en primer plano para reproducir y para cifrar/restaurar.
- Biometría/credencial del dispositivo para desbloquear el baúl.
- Wake lock para mantener la reproducción.
- **Internet** (solo versión de Play) para descargar el anuncio. Si no hay conexión, la app funciona igual y no muestra anuncio.

El usuario puede revocar los permisos desde Ajustes de Android.

## Baúl y nubes

El cifrado protege los archivos en el almacenamiento de la app. Si un vídeo ya estaba respaldado en Google Fotos u otro servicio antes de ocultarlo, esa copia en la nube no se elimina desde VidCofre. Desinstalar la app o borrar sus datos elimina el baúl y su clave de forma irreversible.

## Conservación y eliminación

Los datos locales (biblioteca, ajustes, baúl) se eliminan al desinstalar la app o borrar sus datos. Los datos asociados a la publicidad los gestiona Google según su política; puedes limitar su uso con las opciones de privacidad descritas arriba.

## Menores

VidCofre no está dirigida específicamente a menores y no recopila datos de ninguna persona por su cuenta. La publicidad se configura con una clasificación máxima apta para todos los públicos.

## Cambios

Los cambios de esta política se publicarán en este archivo y en el repositorio.

## Contacto

Abre una incidencia en <https://github.com/jdmontalvo/vidplay/issues>.
