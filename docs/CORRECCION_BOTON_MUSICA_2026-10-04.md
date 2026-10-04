# Botón dedicado con música activa · Estabilización 03

El usuario reportó que Anterior abría la escucha solamente una vez después de iniciar viaje sin música. Con música reproduciéndose, Anterior seguía cambiando de pista incluso en el primer intento. Se interpreta como pérdida del destinatario multimedia; no se dispone del diagnóstico físico de esa sesión.

La versión anterior mantenía MediaSession en PAUSED y reproducía un pulso de ceros de 100 ms, liberado tras 300 ms. La nueva versión mantiene una salida estática de ceros en bucle mientras espera, con PlaybackState coherente en PLAYING. No solicita foco, no abre otro micrófono ni crea SCO. Android 13+ informa el token de la sesión que recibirá las teclas: APP lo observa y comprueba periódicamente, incluyendo música ya activa al iniciar, reproducción reanudada y controles reenviados. Si pierde el token intenta recuperar hasta tres veces por pérdida, separadas al menos un segundo. Si no puede, el diagnóstico informa que Android entrega las teclas a otra app. No se presenta una solicitud de captura como recepción física confirmada.

Antes de cualquier captura de voz, también con Alexa, se libera la salida. Al quedar lista la espera se vuelve a mantener y verificar. Al cerrar viaje se eliminan salida, MediaSession, observador y tareas; los callbacks de una sesión vieja no reactivan audio. Durante un estado de llamada detectado se suspende la salida. El servicio declara reproducción multimedia solo cuando se seleccionó un botón y se concedió el acceso requerido.

La música se pausa según el estado del reproductor elegido, excluyendo la propia salida silenciosa. La tecla reservada se consume; los otros controles se reenvían al reproductor por MediaController. El detector y sus modos de micrófono se conservan. El acceso a notificaciones se utiliza para sesiones multimedia, sin leer mensajes.

Esta técnica mantiene activa una salida de audio mientras espera: debe comprobarse consumo, calidad A2DP y posibles controles multimedia de APP en pantalla bloqueada. No es una garantía contra enrutamiento específico del firmware/fabricante o botones que no llegan a Android. La prueba física con S22+/Android 16 y Y12 Pro queda pendiente.

## APK independiente

El tipo `fieldTest` hereda la compilación debug, conserva todas las funciones y añade `.pruebas` al identificador de la aplicación. Su nombre es **APP · Prueba botón 03**. Se instala aparte porque no se dispone de la clave de firma de la instalación creada por Android Studio del usuario. No reemplaza ni borra sus datos. La copia de prueba tiene sus propios ajustes y permisos; se debe detener el viaje en la copia habitual antes de usarla.

Compilar: `./gradlew clean testFieldTestUnitTest assembleFieldTest lintFieldTest`. APK único con bibliotecas y recursos completos en `app/build/outputs/apk/fieldTest/`; no requiere instalar splits.

## Prueba detenido

1. Instalar **APP · Prueba botón 03** y conceder micrófono, acceso multimedia/notificaciones y permisos usados por las funciones que se prueben. Configurar casco y reproductor en esa copia.
2. Ajustes → Botón dedicado durante el viaje → **Anterior canción → Escuchar orden**. Para cerrar el micro entre órdenes, elegir modo botón o Solo escuchar al pedir una orden. Seleccionar esta copia como asistente si se quiere conservar también la activación larga.
3. Reproducir música antes de iniciar viaje. Iniciar y esperar el diagnóstico **Android confirmó que APP recibe las teclas**. Pulsar Anterior, esperar el tono, decir una orden; la pista no debe retroceder por esa pulsación.
4. Repetir diez veces, esperando a que vuelva a estar listo entre órdenes. Probar también iniciar viaje sin música y empezarla después, pausar/reanudar desde el reproductor y usar Siguiente/Pausa físicos. Cada tecla debe producir solo su acción y el botón reservado debe seguir abriendo la escucha.
5. Repetir con pantalla apagada/bloqueada y Waze al frente. Anotar pulsaciones, capturas y cambios de pista inesperados. Si falla, copiar diagnóstico incluyendo la línea **Botón** y los contadores de recepción/activación.
6. Detener viaje: Anterior debe volver a su función original. Confirmar música normal y ausencia de reaperturas de micrófono o tareas tardías.

Referencias: [selección y entrega de botones multimedia](https://developer.android.com/media/legacy/media-buttons), [observación del destinatario desde Android 13](https://developer.android.com/reference/android/media/session/MediaSessionManager), [selección de sesiones en AOSP](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/media/MediaSessionStack.java).

## Validación local

`testFieldTestUnitTest assembleFieldTest lintFieldTest`: compilación correcta, 81 pruebas sin fallos, lint sin errores (55 advertencias y 9 sugerencias). Se verificaron firma APK v2, integridad ZIP, identificador/etiqueta, bibliotecas arm64 y cinco modelos de voz. APK completo de aproximadamente 78 MB. La matriz física anterior queda pendiente; no se declara resuelto en el casco hasta comprobarla.
