# Fase 0.3 — sesión de voz con pantalla apagada

## Punto de partida

En la fase 0.2 el usuario comprobó en el S22+ que «Escuchar orden» pausa la música y ejecuta «siguiente canción», y que «Alexa» permite reconocer y ejecutar esa orden. En una observación aproximada de cinco minutos con música y sin pronunciar la frase no observó activaciones espontáneas. Es una validación inicial; todavía no hay una medición de tasa de aciertos ni una prueba con ruido real de moto.

## Próxima entrega

1. Mover el ciclo wake word -> STT de una sola orden -> comando multimedia -> reanudación del wake word a un coordinador propiedad de un servicio de primer plano de tipo `microphone`. El servicio conserva la sesión cuando se apaga la pantalla o se abre otra app; la Activity solo inicia/detiene la sesión y muestra su estado. Mantener el modo de prueba actual para poder comparar resultados.
2. Iniciar el servicio únicamente mediante una acción explícita en la app visible, después de conceder `RECORD_AUDIO` y `BLUETOOTH_CONNECT` cuando aplique. Declarar `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, el tipo `microphone` y el permiso de notificaciones en versiones que lo requieran. Mostrar una notificación persistente con estado y acción **Detener**. Si el servicio pierde el permiso, falla el micrófono o Android termina el proceso, dejar la sesión detenida y comunicarlo al reabrir; no intentar arrancar la captura desde `BOOT_COMPLETED` ni a escondidas.
3. Conservar el traspaso Bluetooth comprobado, la pausa de música durante el STT y las defensas contra activaciones falsas. Para la prueba con pantalla apagada ofrecer una señal breve antes de abrir el STT, mientras el detector ya esté pausado, de modo que el usuario sepa cuándo decir la orden sin meter el tono dentro de la captura. Verificar en el dispositivo que la señal se escucha por el casco y no provoca error 7.
4. Mostrar y conservar localmente contadores de activaciones, transcripciones válidas, órdenes ejecutadas, fallos y tiempos wake -> STT / wake -> acción. Un intento pronunciado y un falso positivo necesitan etiqueta del usuario durante la prueba: no inferirlos solo de la puntuación del detector. No guardar grabaciones ni transcripciones de prueba en registros persistentes.

## Implementación para probar

La pantalla añade **Iniciar sesión de viaje** y **Detener sesión de viaje**. El nuevo `TripVoiceService` abre el detector una vez iniciada la notificación de tipo `microphone`; el botón y la acción **Detener** de la notificación lo cierran. También se puede decir **«Alexa»**, esperar la señal y decir **«detener sesión»**; esta orden detiene el servicio y libera el micrófono. «Cancelar» cancela la orden actual, sin terminar la sesión; «cállate» reconoce la petición de silencio, pero aún no hay TTS que interrumpir. «Detener» solo se interpreta después de la frase de activación, nunca mientras el detector espera. La frase y sensibilidad elegidas en la pantalla se aplican al iniciar la sesión. Se conserva la prueba 0.2 con la app visible para comparar. El servicio descarta detecciones durante 1,5 segundos al abrir o reanudar la captura y detiene la sesión después de dos órdenes fallidas o cuatro detecciones en 12 segundos. La señal sonora se emite antes de abrir el reconocedor, con el detector pausado.

En Android 13 o posterior, la pantalla solicita permiso para notificaciones antes de iniciar esta prueba: si se deniega, no se abre la sesión porque no habría un botón **Detener** visible en el panel de notificaciones. El servicio no se reinicia tras una interrupción del proceso; hay que volver a abrir APP y activarlo explícitamente. Los contadores de la última sesión se guardan localmente sin conservar audio ni texto de órdenes; `Transcripciones` indica texto devuelto por STT, aunque no coincida con la orden de prueba. `Acciones` cuenta envíos del evento multimedia, no garantiza que otra app haya cambiado de pista. `Último tiempo` mide desde la detección de la frase hasta el resultado de STT y el envío de la acción, no es una tasa de aciertos.

Si desconectás el casco, detén la sesión desde la notificación. En la siguiente reanudación el servicio detendrá la sesión al no encontrar una entrada Bluetooth de comunicación; la detección inmediata de una desconexión mientras espera la frase aún requiere una prueba adicional en el S22+.

## Criterios de aceptación en el S22+

- Iniciar sesión con la app abierta y el casco conectado; apagar la pantalla o abrir otra app; decir «Alexa» y, tras la señal, «siguiente canción». Verificar cambio de pista y continuidad de escucha para otra orden.
- Detenerla desde la notificación y comprobar que el micrófono queda libre, deja de reaccionar a «Alexa» y la notificación desaparece. Repetir el cierre con **«Alexa» → «detener sesión»**, verificando el mismo resultado. Repetir con Bluetooth desconectado y registrar un error claro sin bucle.
- Comparar diez intentos con el teléfono a distancia y cinco minutos de silencio, registrar por separado aciertos y falsos positivos; repetir con música y, estando la moto inmóvil, con motor apagado y encendido. Comprobar consumo y estabilidad de la sesión antes de considerarla apta para pruebas de ruta.
- Mantener el STT por orden corta; no dejar `SpeechRecognizer` continuamente activo.

## Límite de esta entrega

Esta fase no añade lectura de Waze, LLM ni acciones sensibles. Después de validar la sesión y sus métricas en el teléfono, cerrar la fase 0 y abrir la fase 1 para ampliar Intent Router y Action Executor.
