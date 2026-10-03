# Activación manual opcional y prueba del Y12 Pro

Rama: `feature/fase-1-intent-router`.

## Sesión con micrófono cerrado en espera

En Ajustes → entrada, activar **Solo escuchar al pedir una orden**. Se conserva el modo elegido, incluso modo 1 continuo, pero su proveedor efectivo pasa a botón: no se crea el detector de palabra ni su grabador. «Alexa» no activa mientras esté habilitada esta opción.

El viaje permanece activo; una solicitud manual abre la ventana de orden usando la ruta de voz del casco. Al finalizar o vencer el tiempo de escucha se libera la ruta y vuelve la espera con micrófono cerrado. Las confirmaciones y el dictado que formen parte de esa orden siguen su flujo existente. La música puede bajar a calidad de llamada mientras se usa el micrófono; no se promete calidad A2DP durante ese intervalo.

La opción se guarda. Puede cambiarse antes del viaje o cuando esté listo para otra orden. En sesión se cierra el proveedor anterior y se verifica la devolución de la ruta antes de crear el siguiente; no se reinician las métricas ni se abren dos proveedores. Una llamada, una orden en proceso o una restauración bloquean el cambio. Cambiar el modo por voz conserva la opción de activación manual. Desactivarla devuelve la política del modo seleccionado; modo 2 continúa siendo manual.

Por ahora se puede activar con la pulsación de asistente ya soportada por el casco, la app, la burbuja o «Escuchar orden» en la notificación. **El toque corto del botón pequeño todavía no está asignado en producción.** Primero hay que demostrar que Android lo entrega a APP y distinguirlo del botón M y los controles de música.

## Prueba física (moto detenida)

1. Detener el viaje y las demás pruebas de audio. Conectar el Y12; mantener APP visible en Pruebas.
2. Para comparar la pulsación larga, elegir APP como asistente antes de empezar.
3. Iniciar **Prueba del botón Y12 · 60 segundos**. APP emite un tono de 150 ms por la salida musical normal: Android moderno prioriza sesiones que hayan reproducido audio localmente. Durante ese minuto, solicita temporalmente los botones multimedia y pueden dejar de controlar Poweramp. No abre SCO, no graba, no pausa la música ni solicita foco de audio. El tono no garantiza prioridad en todos los teléfonos.
4. Pulsar una vez el botón pequeño con icono de micrófono, esperar y repetir dos veces. Luego probar una pulsación larga del mismo botón. Finalmente, pulsar M una vez como comparación. Anotar cualquier cambio del intercomunicador y si se siguió esa secuencia.
5. Detener y copiar el registro. También finaliza a los 60 s, al salir de la pantalla de pruebas o al dejar APP. Se libera la sesión sin enviar play/pause al reproductor.

El registro muestra fuente, código de tecla, DOWN/UP, repeticiones y duración, o ASSIST/VOICE_COMMAND. Una invocación de asistente recibida durante la prueba sólo se registra, sin iniciar voz. Se mantiene esa protección durante cinco segundos después de cerrarla, incluso si Android recrea la Activity, para no convertir una invocación tardía de la prueba en una orden de voz. Tras ese plazo vuelve la activación normal de asistente.

MediaSession sólo recibe los eventos que Android le enruta; no observar un evento no demuestra que el casco no lo envíe. Tampoco se identifica el botón físico a partir del código sin comparar las pulsaciones. Un firmware que use internamente el toque corto para emparejar/intercom no puede reasignarse desde esta app por ese hecho.

## Verificación en el teléfono

- Modo 1 + opción activada: viaje listo, música normal, micrófono cerrado; decir «Alexa» no abre una orden.
- Activación manual: una orden funciona, termina su flujo y vuelve a cerrar el micrófono. Repetir con silencio, cancelación, apertura de apps, llamada y regreso de la llamada.
- Activar/desactivar la opción durante espera: mismo viaje y métricas; bloqueo mientras escucha, confirma, llama o restaura.
- Cambiar a modos 1/2/3 con la opción activada: nunca vuelve la escucha de palabra hasta desactivarla.
- Detener la prueba del botón o dejar APP: controles de Poweramp vuelven a su enrutamiento habitual; no se ha abierto ningún micrófono.

La compilación, pruebas unitarias y lint no sustituyen esta prueba física de Bluetooth/Android.


## Estabilización del núcleo y botón configurable

Documento 04 aplicado: captura opcional de un control multimedia durante el viaje, métricas por etapa y extracción gradual del servicio. La recepción del botón con Poweramp/Waze/pantalla bloqueada requiere regresión física.

[Diseño y alcance](ESTABILIZACION_1_0.md) · [Pruebas S22+/Y12](PRUEBA_ESTABILIZACION_1_0.md)
