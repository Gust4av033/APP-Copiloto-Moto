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

El volumen cambia un paso solo cuando el usuario da la orden. Con Bluetooth SCO y modo comunicación activos se prueba el nivel de comunicación del casco; fuera de esa ruta se usa el nivel multimedia. La pantalla muestra ambos valores y la ruta que Android informa, sin elevar ninguno automáticamente. Si el cambio de nivel no modifica el sonido real en el Y12, registrar el diagnóstico y mantener el control físico del teléfono para la prueba estacionaria.

Mientras la sesión espera «Alexa», el detector *sí* graba audio localmente con el micrófono Bluetooth; solo el reconocimiento de órdenes (STT) se abre después de la frase. La grabación de Bluetooth clásico puede cambiar la calidad de la música por el perfil de comunicación del casco. No se puede detectar «Alexa» sin algún micrófono activo. Para conservar el audio de música de alta calidad habría que probar el micrófono del teléfono para la frase de activación o un dispositivo LE Audio compatible; esa ruta aún no se ha validado en el casco y no se cambia automáticamente durante este viaje.

### Prueba experimental posterior: música mientras espera Alexa

La sesión de viaje ofrece ahora dos rutas elegibles **antes** de iniciarla. **Casco: activación y orden** es la ruta previamente validada y la opción predeterminada. **Prueba: teléfono para Alexa, casco para la orden** quita la ruta Bluetooth de comunicación durante la espera, verifica que la grabación de activación use el micrófono integrado y la vuelve a seleccionar cuando oye Alexa, antes de abrir el STT. Si Android no informa el micrófono o usa otra entrada, detiene esa sesión con un mensaje; no afirma haber resuelto el problema sin medirlo en el S22+. La librería de wake no permite forzar un dispositivo de entrada: Android decide la entrada normal y APP comprueba el resultado. Durante la orden el audio puede volver brevemente a calidad de llamada.

Para comparar, con la moto **detenida**, reproducí una misma canción y alterná sesiones en ambos modos: anotá cómo se escucha mientras espera Alexa, si la app informa «Alexa: micrófono del teléfono», cuántas veces se detecta la frase de diez intentos y cuántas órdenes entiende por casco. Probá el teléfono en su posición real de viaje y no dependás de esta función para controlar la música durante la conducción hasta confirmar que Alexa se escucha con viento y ruido. Si va guardado, la voz desde el casco puede no llegar al micrófono del celular. No se graba ni guarda audio.

### Prueba opcional: botón de asistente del Y12

En el Y12 probado, mantener pulsado el botón central abre Gemini: Android dirige esa pulsación a un asistente de voz. APP se anuncia como candidata para el rol de asistente mediante `ACTION_ASSIST` y también acepta `ACTION_VOICE_COMMAND`, la otra acción pública de Android que puede lanzar una orden de voz desde un auricular. Elegir APP es opcional y se solicita explícitamente desde **Pruebas → Elegir APP como asistente**. Para volver a Gemini, elegilo otra vez en Ajustes → Aplicaciones → Aplicaciones predeterminadas → Aplicación de asistente digital (el nombre del menú puede variar). No se intenta interceptar las teclas multimedia de otras aplicaciones.

Con el casco conectado y la moto detenida, **sin iniciar sesión de viaje**, elegí APP, poné música y mantené pulsado el botón del Y12. Si el teléfono entrega `ACTION_ASSIST` o `ACTION_VOICE_COMMAND`, APP abre **Pruebas**, muestra la acción recibida y usa **Orden corta**: esperá **Reconocedor listo**, decí por el casco «siguiente canción» o «subir volumen» y comprobá la acción. Durante la espera del botón, esta ruta no mantiene el micrófono de APP abierto ni fuerza la ruta de comunicación; al escuchar una orden puede pasar brevemente a calidad de llamada. La prueba de botón no admite llamadas todavía; la confirmación de llamadas sigue en la sesión de viaje. Si solo aparece un selector del sistema, registrar su pantalla: no hay confirmación de que Samsung haya entregado el intent a APP. Las restricciones de Samsung con pantalla bloqueada y el comportamiento concreto del botón deben validarse en el S22+ antes de usarla en ruta.

Los toques cortos de siguiente y anterior canción que el Y12 ya envía al teléfono son el control preferido para música durante el viaje. La pulsación larga para una orden de voz queda como respaldo o para órdenes sin botón físico; no se exige mantenerla pulsada para cambiar canciones.

### Prueba Y12 durante la sesión de viaje

Con APP elegida como asistente, iniciá la sesión usando **Casco: activación y orden**. Esperá a que la pantalla o la notificación indique que espera «Alexa». Con la moto detenida, mantené pulsado el botón central del Y12: APP debe mostrar **Y12: abriendo reconocedor** y luego **Y12: reconocedor listo**. Decí «siguiente canción» al estar listo y comprobá que cambie la pista. Repetí con «pausa» y «reanudar»; tras cada orden debe volver a esperar «Alexa» y admitir otra pulsación. La pantalla **Resultados del viaje** cuenta por separado las activaciones Alexa y las órdenes iniciadas con Y12. Si pulsás durante otra orden, se informa que la sesión está ocupada y no se abre una segunda captura.

Si el botón abre APP sin iniciar el reconocedor, anotá el estado exacto mostrado en Viaje y si la sesión seguía activa. Probá después con la pantalla apagada y con otra app al frente, siempre estando detenido. No des por validada la ruta del casco solo porque Android haya entregado `VOICE_COMMAND`: también debe llegar a STT y ejecutar la acción.

### Pantalla de prueba ordenada

**Viaje** muestra el estado, el botón principal, los comandos y las métricas; los tiempos detallados se despliegan si hacen falta. **Pruebas** agrupa el botón del casco, la orden corta, el diagnóstico de micrófono y la activación local con la app visible. **Ajustes** agrupa frase y sensibilidad, ruta del micrófono y contactos. Esta organización no cambia los permisos ni las reglas que confirman una llamada.

En Android 13 o posterior, la pantalla solicita permiso para notificaciones antes de iniciar esta prueba: si se deniega, no se abre la sesión porque no habría un botón **Detener** visible en el panel de notificaciones. El servicio no se reinicia tras una interrupción del proceso; hay que volver a abrir APP y activarlo explícitamente. Los contadores de la última sesión se guardan localmente sin conservar audio ni texto de órdenes; `Transcripciones` indica texto devuelto por STT, aunque no coincida con la orden de prueba. `Acciones` cuenta envíos del evento multimedia, no garantiza que otra app haya cambiado de pista. `Último tiempo` mide desde la detección de la frase hasta el resultado de STT y el envío de la acción, no es una tasa de aciertos.

Si desconectás el casco, detén la sesión desde la notificación. En la siguiente reanudación el servicio detendrá la sesión al no encontrar una entrada Bluetooth de comunicación; la detección inmediata de una desconexión mientras espera la frase aún requiere una prueba adicional en el S22+.

## Prueba prioritaria: volumen bajo con Y12

Hacer esta comparación con la moto detenida, el S22+ y el Y12 conectados, y la misma canción reproduciéndose. No subir el volumen automáticamente desde APP: primero identificar qué ruta y nivel cambian al iniciar el viaje.

1. Con la sesión detenida, entrar a **Pruebas → Audio del casco · diagnóstico → Actualizar niveles**. Anotar literalmente **Modo, Ruta, Música y Comunicación**, además de cómo se oye la canción.
2. Iniciar **Casco: activación y orden**. Esperar a que indique **Esperando «Alexa»** y actualizar el diagnóstico. Anotar los cuatro datos de nuevo y si bajaron volumen, calidad o ambos.
3. Con la sesión activa, pulsar **una vez** el botón físico de subir volumen del Y12 y actualizar el diagnóstico. Anotar qué valor cambió y si se oyó más fuerte. Los toques cortos de siguiente/anterior canción siguen siendo el control normal de pistas; no mantener pulsado para cambiarlas.
4. Detener la sesión desde la app o la notificación, actualizar el diagnóstico y comprobar si regresan el volumen y la calidad iniciales.

Registrar los valores **antes / durante / después**, junto con la versión de Android y la app musical usada. Si Música se mantiene alta, Comunicación baja y el sonido mejora solo al ajustar Comunicación, probar un ajuste voluntario por voz en la ruta SCO. Si los niveles no explican el cambio, investigar el perfil Bluetooth y la ruta de reproducción antes de modificar el volumen. La ruta con micrófono del teléfono no resultó fiable en la posición de viaje observada; usarla solo como comparación estacionaria. No considerar apta la escucha continua en ruta hasta comprobar el volumen y la activación con el Y12 real.

## Criterios de aceptación en el S22+

- Iniciar sesión con la app abierta y el casco conectado; apagar la pantalla o abrir otra app; decir «Alexa» y, tras la señal, «siguiente canción». Verificar cambio de pista y continuidad de escucha para otra orden.
- Con la sesión aún activa, probar **«Alexa» → «pausa»**, confirmar que la música queda detenida y luego **«Alexa» → «reanudar»**. Probar «anterior canción» y «siguiente canción» en intentos independientes. Si se dijo «detener» a secas, comprobar que solo se pausó la música.
- Detenerla desde la notificación y comprobar que el micrófono queda libre, deja de reaccionar a «Alexa» y la notificación desaparece. Repetir el cierre con **«Alexa» → «detener sesión»**, verificando el mismo resultado. Repetir con Bluetooth desconectado y registrar un error claro sin bucle.
- Comparar diez intentos con el teléfono a distancia y cinco minutos de silencio, registrar por separado aciertos y falsos positivos; repetir con música y, estando la moto inmóvil, con motor apagado y encendido. Comprobar consumo y estabilidad de la sesión antes de considerarla apta para pruebas de ruta.
- Mantener el STT por orden corta; no dejar `SpeechRecognizer` continuamente activo.

## Límite de esta entrega

El objetivo de esta fase sigue siendo validar el flujo manos libres con casco, activación, botón Y12, STT, multimedia y métricas. Las llamadas incluidas como prueba experimental no cierran la Fase 1. Después de validar la sesión y sus métricas en el S22+, cerrar la Fase 0 y ampliar Intent Router y Action Executor en una rama de Fase 1.
