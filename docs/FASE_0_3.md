# Fase 0.3 — sesión de voz con pantalla apagada

## Punto de partida

En la fase 0.2 el usuario comprobó en el S22+ que «Escuchar orden» pausa la música y ejecuta «siguiente canción», y que «Alexa» permite reconocer y ejecutar esa orden. En una observación aproximada de cinco minutos con música y sin pronunciar la frase no observó activaciones espontáneas. Es una validación inicial; todavía no hay una medición de tasa de aciertos ni una prueba con ruido real de moto.

## Próxima entrega

1. Mover el ciclo wake word -> STT de una sola orden -> comando multimedia -> reanudación del wake word a un coordinador propiedad de un servicio de primer plano de tipo `microphone`. El servicio conserva la sesión cuando se apaga la pantalla o se abre otra app; la Activity solo inicia/detiene la sesión y muestra su estado. Mantener el modo de prueba actual para poder comparar resultados.
2. Iniciar el servicio únicamente mediante una acción explícita en la app visible, después de conceder `RECORD_AUDIO` y `BLUETOOTH_CONNECT` cuando aplique. Declarar `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, el tipo `microphone` y el permiso de notificaciones en versiones que lo requieran. Mostrar una notificación persistente con estado y acción **Detener**. Si el servicio pierde el permiso, falla el micrófono o Android termina el proceso, dejar la sesión detenida y comunicarlo al reabrir; no intentar arrancar la captura desde `BOOT_COMPLETED` ni a escondidas.
3. Conservar el traspaso Bluetooth comprobado, la pausa de música durante el STT y las defensas contra activaciones falsas. Para la prueba con pantalla apagada ofrecer una señal breve antes de abrir el STT, mientras el detector ya esté pausado, de modo que el usuario sepa cuándo decir la orden sin meter el tono dentro de la captura. Verificar en el dispositivo que la señal se escucha por el casco y no provoca error 7.
4. Mostrar y conservar localmente contadores de activaciones, transcripciones válidas, órdenes ejecutadas, fallos y tiempos wake -> STT / wake -> acción. Un intento pronunciado y un falso positivo necesitan etiqueta del usuario durante la prueba: no inferirlos solo de la puntuación del detector. No guardar grabaciones ni transcripciones de prueba en registros persistentes.

## Implementación para probar

La pantalla añade **Iniciar sesión de viaje** y **Detener sesión de viaje**. El nuevo `TripVoiceService` abre el detector una vez iniciada la notificación de tipo `microphone`; el botón y la acción **Detener** de la notificación lo cierran. También se puede decir **«Alexa»**, esperar la señal y decir **«detener sesión»**; esta frase completa detiene el servicio y libera el micrófono. «Cancelar» termina ese intento sin ejecutar multimedia y mantiene la sesión; «cállate» reconoce la petición de silencio, pero aún no hay TTS que interrumpir. La frase y sensibilidad elegidas en la pantalla se aplican al iniciar la sesión. Se conserva la prueba 0.2 con la app visible para comparar. El servicio descarta detecciones durante 1,5 segundos al abrir o reanudar la captura y detiene la sesión después de dos órdenes fallidas o cuatro detecciones en 12 segundos. La señal sonora se emite antes de abrir el reconocedor, con el detector pausado.

### Órdenes de música de esta prueba

| Tras «Alexa» y la señal, decir | Evento enviado |
| --- | --- |
| «reanudar», «reproducir», «play» | Reproducir (`MEDIA_PLAY`) |
| «pausa», «pausar», «detener» | Pausar música (`MEDIA_PAUSE`) |
| «anterior canción», «anterior» | Pista anterior (`MEDIA_PREVIOUS`) |
| «siguiente canción», «siguiente» | Pista siguiente (`MEDIA_NEXT`) |
| «subir volumen», «más volumen» | Aumentar un paso el volumen multimedia (`VOLUME_UP`) |
| «bajar volumen», «menos volumen» | Disminuir un paso el volumen multimedia (`VOLUME_DOWN`) |
| «detener sesión» | Cerrar detector, servicio y notificación |

Las frases se comparan de forma local y exacta después de normalizar mayúsculas y acentos; otras palabras pueden devolver «orden desconocida». «Detener» **sin** «sesión» pausa la música y deja el asistente esperando otra «Alexa». Reproducir y pausar envían teclas separadas, no un botón alternante. Al pedir pausa, APP no vuelve a reproducir automáticamente la música que detuvo para escuchar esa orden. El envío de la tecla no demuestra por sí solo que la aplicación de música obedeció.

El volumen se ajusta sobre `STREAM_MUSIC`, un paso por orden; la pantalla muestra el valor actual y máximo informado por Android. El audio de llamadas utiliza otro volumen y la app no lo modifica. Para probar las órdenes de volumen sin depender todavía de la ruta experimental, seleccioná la opción de casco ya validada.

Mientras la sesión espera «Alexa», el detector *sí* graba audio localmente con el micrófono Bluetooth; solo el reconocimiento de órdenes (STT) se abre después de la frase. La grabación de Bluetooth clásico puede cambiar la calidad de la música por el perfil de comunicación del casco. No se puede detectar «Alexa» sin algún micrófono activo. Para conservar el audio de música de alta calidad habría que probar el micrófono del teléfono para la frase de activación o un dispositivo LE Audio compatible; esa ruta aún no se ha validado en el casco y no se cambia automáticamente durante este viaje.

### Prueba experimental posterior: música mientras espera Alexa

La sesión de viaje ofrece ahora dos rutas elegibles **antes** de iniciarla. **Casco: activación y orden** es la ruta previamente validada y la opción predeterminada. **Prueba: teléfono para Alexa, casco para la orden** quita la ruta Bluetooth de comunicación durante la espera, verifica que la grabación de activación use el micrófono integrado y la vuelve a seleccionar cuando oye Alexa, antes de abrir el STT. Si Android no informa el micrófono o usa otra entrada, detiene esa sesión con un mensaje; no afirma haber resuelto el problema sin medirlo en el S22+. La librería de wake no permite forzar un dispositivo de entrada: Android decide la entrada normal y APP comprueba el resultado. Durante la orden el audio puede volver brevemente a calidad de llamada.

Para comparar, con la moto **detenida**, reproducí una misma canción y alterná sesiones en ambos modos: anotá cómo se escucha mientras espera Alexa, si la app informa «Alexa: micrófono del teléfono», cuántas veces se detecta la frase de diez intentos y cuántas órdenes entiende por casco. Probá el teléfono en su posición real de viaje y no dependás de esta función para controlar la música durante la conducción hasta confirmar que Alexa se escucha con viento y ruido. Si va guardado, la voz desde el casco puede no llegar al micrófono del celular. No se graba ni guarda audio.

### Prueba opcional: botón de asistente del Y12

En el Y12 probado, mantener pulsado el botón central abre Gemini: Android dirige esa pulsación al asistente predeterminado. APP ahora se anuncia a Android como candidata para ese rol mediante una actividad `ACTION_ASSIST`; elegirla es opcional y se solicita explícitamente desde **Elegir APP como asistente del botón**. Para volver a Gemini, elegilo otra vez en Ajustes → Aplicaciones → Aplicaciones predeterminadas → Aplicación de asistente digital (el nombre del menú puede variar). No se intenta interceptar las teclas multimedia de otras aplicaciones.

Con el casco conectado y la moto detenida, **sin iniciar sesión de viaje**, elegí APP, poné música y mantené pulsado el botón del Y12. Si el teléfono entrega la invocación `ACTION_ASSIST`, APP abre su pantalla y usa la prueba **Orden corta** ya existente: espera a que indique **Reconocedor listo**, escuchá por el casco «siguiente canción» o «subir volumen» y comprobá la acción. Durante la espera del botón, esta ruta no mantiene el micrófono de APP abierto ni fuerza la ruta de comunicación; al escuchar una orden puede pasar brevemente a calidad de llamada. La prueba de botón no admite llamadas todavía; la confirmación de llamadas sigue en la sesión de viaje. Las restricciones de Samsung con pantalla bloqueada y el comportamiento concreto del botón deben validarse en el S22+ antes de usarla en ruta.

En Android 13 o posterior, la pantalla solicita permiso para notificaciones antes de iniciar esta prueba: si se deniega, no se abre la sesión porque no habría un botón **Detener** visible en el panel de notificaciones. El servicio no se reinicia tras una interrupción del proceso; hay que volver a abrir APP y activarlo explícitamente. Los contadores de la última sesión se guardan localmente sin conservar audio ni texto de órdenes; `Transcripciones` indica texto devuelto por STT, aunque no coincida con la orden de prueba. `Acciones` cuenta envíos del evento multimedia, no garantiza que otra app haya cambiado de pista. `Último tiempo` mide desde la detección de la frase hasta el resultado de STT y el envío de la acción, no es una tasa de aciertos.

Si desconectás el casco, detén la sesión desde la notificación. En la siguiente reanudación el servicio detendrá la sesión al no encontrar una entrada Bluetooth de comunicación; la detección inmediata de una desconexión mientras espera la frase aún requiere una prueba adicional en el S22+.

## Criterios de aceptación en el S22+

- Iniciar sesión con la app abierta y el casco conectado; apagar la pantalla o abrir otra app; decir «Alexa» y, tras la señal, «siguiente canción». Verificar cambio de pista y continuidad de escucha para otra orden.
- Con la sesión aún activa, probar **«Alexa» → «pausa»**, confirmar que la música queda detenida y luego **«Alexa» → «reanudar»**. Probar «anterior canción» y «siguiente canción» en intentos independientes. Si se dijo «detener» a secas, comprobar que solo se pausó la música.
- Detenerla desde la notificación y comprobar que el micrófono queda libre, deja de reaccionar a «Alexa» y la notificación desaparece. Repetir el cierre con **«Alexa» → «detener sesión»**, verificando el mismo resultado. Repetir con Bluetooth desconectado y registrar un error claro sin bucle.
- Comparar diez intentos con el teléfono a distancia y cinco minutos de silencio, registrar por separado aciertos y falsos positivos; repetir con música y, estando la moto inmóvil, con motor apagado y encendido. Comprobar consumo y estabilidad de la sesión antes de considerarla apta para pruebas de ruta.
- Mantener el STT por orden corta; no dejar `SpeechRecognizer` continuamente activo.

## Límite de esta entrega

Esta fase no añade lectura de Waze, LLM ni acciones sensibles. Después de validar la sesión y sus métricas en el teléfono, cerrar la fase 0 y abrir la fase 1 para ampliar Intent Router y Action Executor.
