# Estabilización 06: señal para hablar y controles del casco

El usuario confirmó que la actualización 05 recibe pulsaciones consecutivas del botón elegido, incluso con música. Esta revisión conserva esa política y aborda la señal para hablar, la confirmación de apertura de apps y el registro de controles como avance acotado de Fase 1.

## Escucha y señal

- Las capturas del viaje usan una señal común de dos pulsos después de `onReadyForSpeech`, incluida la orden principal, la elección de app, el dictado y la confirmación de llamada saliente. Antes, la orden principal emitía su tono antes de iniciar el reconocedor.
- La señal usa la ruta de comunicación seleccionada, sin modificar el volumen. Si Android rechaza su preparación o reproducción, se termina la captura y se muestra el fallo. El diagnóstico registra salida solicitada y volumen: iniciar un AudioTrack no demuestra que el sonido se haya oído físicamente en el casco.
- La ventana de confirmación pasa de 12 a 18 segundos desde la primera disponibilidad del reconocedor. La preparación tiene su propio límite de 10 segundos; no consume esos 18 segundos.
- Si una confirmación termina por silencio/no coincidencia antes de detectar voz, puede reabrirse hasta dos veces, con una nueva señal y al menos dos segundos restantes. Los reintentos conservan el plazo original, la pausa de música y el foco. No repiten la acción externa, no recuperan errores de audio y no reinician después de detectar voz.
- Se elimina el temporizador paralelo de confirmación de llamada que comenzaba antes de preparar el reconocedor. La captura mantiene un único plazo y conserva las comprobaciones de cancelación.
- Orden y dictado conservan sus límites de 15 y 40 segundos. La captura sigue esperando a que termine la locución previa. WhatsApp conserva su flujo de borrador, sin envío automático.

## Perfil de controles observados

En Ajustes y Pruebas se muestra el casco resuelto por el selector de ruta y los controles recibidos con él conectado. La selección del botón sacrificado sigue siendo explícita y reversible.

- Se guardan códigos multimedia recibidos e invocaciones del asistente. No se infieren controles por el nombre Y12, Dime u otro modelo.
- Una identidad Bluetooth disponible permite recuperar el perfil tras reconectar. Se usa su hash en preferencias locales; el nombre es sólo una etiqueta. Dos dispositivos con el mismo nombre no comparten automáticamente evidencia.
- Si Android no proporciona una identidad estable, el perfil es temporal, se informa en pantalla y se descarta al desconectar.
- Un control no recibido figura como **sin comprobar**, nunca como ausencia de botones. Los eventos virtuales de Android pueden no identificar el origen físico: esta evidencia describe señales observadas en ese contexto, no un inventario certificado del hardware.
- El acceso «Comprobar controles» lleva a la prueba visible, donde deben pulsarse los controles uno por uno. Después del tono de inicio se mantiene una salida silenciosa durante esa prueba para favorecer la entrega de eventos a su sesión. El límite sigue siendo 60 segundos y salir de la pantalla detiene la prueba.
- Los botones de volumen conservan su comportamiento y no se incorporan al perfil multimedia.

## Comprobación en teléfono y casco

1. Actualizar la rama, instalar con Android Studio y comprobar «Estabilización 06» en la app. Elegir Y12 y el mismo botón de la versión 05.
2. Con música, pulsar el botón diez veces en ciclos completos de orden. Comprobar que cada captura tiene señal en el casco y que la música se recupera al finalizar.
3. Pedir abrir una app y responder 1 o 2 **después de la señal**, tanto de inmediato como tras unos segundos. Probar también cancelar y no responder: no debe abrirse una app por una captura anterior ni quedar el micrófono activo.
4. Dictar un borrador de WhatsApp tras una locución larga: comprobar que la señal llega después de la locución y del reconocedor preparado, y que la captura no consume su plazo durante esa locución.
5. En «Comprobar controles», probar anterior, siguiente, pausa/reproducción y asistente tres veces cada uno. Revisar el perfil, reconectar y comprobar su persistencia cuando hay identidad estable. Repetir con otro casco y comprobar que no hereda los controles del Y12.
6. Si falta una señal, copiar el diagnóstico: eventos `READY_CUE_STARTED`/`READY_CUE_FAILED`, salida solicitada, volumen, reintentos por silencio y entradas activas permiten distinguir un fallo de inicio de una salida que no se oye.

La audibilidad real, los tiempos del motor de voz y la atribución de las señales requieren estas pruebas físicas. Las pruebas JVM cubren plazos y límites de recuperación, identidad de perfiles y registro de señales; no sustituyen el ensayo Bluetooth.

## Referencias de Android

- [RecognitionListener.onReadyForSpeech](https://developer.android.com/reference/android/speech/RecognitionListener#onReadyForSpeech(android.os.Bundle)): disponibilidad para empezar a hablar.
- [RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent): las sugerencias de silencio pueden ser ignoradas por el motor.
- [AudioTrack](https://developer.android.com/reference/android/media/AudioTrack): en modo estático, la inicialización se completa después de cargar los datos; su estado de reproducción no certifica audibilidad externa.

## Validación de esta revisión

Validación realizada con JDK 17, Gradle 8.13 y SDK 36:

- `testDebugUnitTest assembleDebug lintDebug`: **BUILD SUCCESSFUL**.
- **94 pruebas JVM**, distribuidas en 22 suites, sin fallos, errores ni omisiones. Incluyen la política de pulsaciones consecutivas, plazos, recuperación acotada de confirmaciones y evidencia por dispositivo.
- Lint: **0 errores**, 57 advertencias y 9 sugerencias. Las advertencias no bloquean la compilación; incluyen una sugerencia de usar la extensión KTX para editar las preferencias del perfil.
- APK debug generado para validar el empaquetado, con modelos y bibliotecas locales conservados. El usuario instala desde su rama con Android Studio; no se ha probado esta revisión en un teléfono o casco físico.
- `git diff --check`: correcto.
