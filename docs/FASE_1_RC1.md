# Fase 1 · RC1: reconocimiento y UI glass

Rama integrada: `release/fase-1-rc1`. Versión Android: `1.0.0-rc1`, código 3. Base: `feature/ui-glass-redesign` en `4e99900f55fff8936e7f96fc43f9243bff115734`, que incluye Escucha 03 (`1c826ff7`). Esta candidata reúne diseño y lógica. La UI vigente de `1d58c29` se integra por indicación del usuario y el trabajo continúa en `release/fase-1-rc1`; `main` conserva su estado.

## Estado de la evidencia

El usuario reportó el 12 de octubre que llamadas, botón dedicado, apertura de Waze/destinos/búsquedas y preparación del borrador de WhatsApp funcionan. La versión exacta de esa prueba no quedó identificada. Es evidencia de campo del avance anterior, no una validación del reconocimiento bilingüe ni de los cambios de cancelación de RC1.

RC1 es una **candidata a estable del alcance implementado**. No se declara cerrada toda Fase 1 ni se promete control de cualquier app. El envío de WhatsApp continúa manual. Interrumpir TTS diciendo «silencio» mientras APP habla, llamadas entrantes por voz y grabar/enviar notas de voz continúan pendientes; Silencio desde la notificación es la interrupción disponible.

## Reconocimiento sin elegir idioma por orden

Órdenes normales: «pon Sweet Child O’ Mine», «reproduce Don't Stop Me Now», «busca la canción Nothing Else Matters» o «play [título]». El título conserva su escritura; no se cambia su idioma ni se transforma en un comando.

APP consulta modelos instalados del reconocedor local. En Android 14+ y con español e inglés disponibles, solicita cambio automático restringido a esos idiomas para órdenes y dictado. Las confirmaciones mantienen español; el usuario sigue usando uno/dos o confirmar llamada/cancelar. No se cambia a reconocimiento en nube.

Tener modelos no prueba que el fabricante implemente el cambio: Android permite ignorar estas opciones. Ajustes → Activación muestra disponibilidad y el último idioma informado; un cambio observado se muestra solo si Android lo reporta. Si no hay soporte comprobado o falta inglés, se conserva el reconocimiento español y se informa el estado. Si el motor rechaza el reconocimiento automático con un error de idioma, la captura termina sin ejecutar nada y las siguientes usan español hasta volver a comprobar idiomas. El comando explícito de inglés anterior sigue como respaldo compatible, pero ya no se presenta como requisito de la interacción normal.

«Comprobar idiomas» no abre el micrófono ni pide foco de audio. «Preparar inglés» solicita el modelo al sistema mediante una acción del usuario, con el viaje detenido; Android puede pedir interacción o programar la descarga. No hay descarga automática ni una preferencia manual por cada canción.

## Comandos y ambigüedad

- Música: variantes españolas y `next track`, `previous song`, `pause music`, `resume music`, `play [título]`.
- Volumen: `volume up/down`, `set volume to forty percent`; el nivel absoluto conserva el rango 0–15 y porcentajes 0–100. Fuera de rango no ejecuta un ajuste.
- Apps/contactos: `open Spotify`, `call John Smith`, «márcale a Ana». La búsqueda usa nombres reales y conserva los requisitos de unicidad/permisos.
- Aperturas vivas: uno/dos, «sí abre»/«mejor no». Una afirmación genérica no confirma una llamada. «Envía» o `send` no se interpreta como envío de un borrador.
- Contactos ambiguos: se nombran hasta tres coincidencias y se pide nombre completo. Nombres idénticos o varios números reciben instrucciones específicas para corregir la agenda/elegir un contacto fijo. No se escoge arbitrariamente persona ni número.
- Fallos al resolver apps, chats o contactos se explican por voz desde el casco, sin despachar una acción; quedan en Último resultado. Se puede cortar esa explicación con Silencio desde la notificación.

## Tonos y tiempos comunes

| Etapa | Política RC1 |
| --- | --- |
| Preparación del reconocedor | Hasta 10 s; consulta de idiomas limitada a 1,8 s y cacheada. |
| Señal para hablar | Dos pulsos, 360 ms; se solicita después de READY. Se usa también en Pruebas. |
| Orden | Ventana de 15 s desde READY. |
| Confirmación de app, chat, borrador o llamada | Ventana de 18 s desde READY. |
| Dictado | Ventana de 40 s desde READY. |
| Tras TTS | Drenaje de 300 ms; reelección del casco y dos lecturas de ruta estable separadas 250 ms; máximo 4 s. |
| Cancelación de interacción activa | Un tono grave de 160 ms, distinto al de hablar, cuando la ruta admite emitirlo. |
| Regreso a espera | Retardo común de 300 ms, seguido de la comprobación de la ruta del modo elegido. |

El endpointer del motor puede cerrar por silencio antes de la ventana. Se permiten hasta dos reaperturas sin voz detectada, conservando el plazo total. El tono no confirma audibilidad: el reporte distingue inicio aceptado y observación física. No se fuerza el volumen.

Silencio durante la confirmación de llamada invalida anuncio, preparación y respuesta pendiente; una respuesta antigua no puede marcar. Las cancelaciones conservan la intención de música pausada/reproduciéndose y el regreso depende de restaurar la ruta, no solamente de esperar un tiempo fijo. En modo voz continua la calidad puede permanecer limitada por SCO; restauración al modo elegido no equivale a calidad A2DP.

## Burbuja compacta 02

La esfera de agua de la UI actual se conserva. El panel abierto ocupa un ancho habitual de 244 dp, mide su altura según contenido y reúne modo/estado, Pedir una orden, Abrir APP y Ocultar. Fuente grande y ventanas estrechas reordenan los accesos en vertical; Ocultar permanece accesible fuera del scroll. Borde doble, reflejos y relieve de botones comparten la paleta glass. La lógica de captura y recuperación de RC1 no cambia. [Diseño, evidencia y limitaciones](UI_BASE.md) · [regresión R19–R22](REGRESION_FASE_1_RC1.md). El clima y módulos del overlay completo quedan como dirección futura.

## Correspondencia con UI

| Capacidad | Superficie | Prueba |
| --- | --- | --- |
| Disponibilidad y preparación de idiomas | Ajustes → Activación; tarjeta glass con iconos y controles bloqueados cuando el audio está ocupado. | Idiomas presentes/ausentes, descarga programada, fallo y reconsulta. |
| Nuevas frases | Comandos, prueba local de frase y guía de Música. | Título mixto preservado, controles con prioridad, rango de volumen. |
| Ambigüedad | Feedback hablado y Último resultado en Viaje. | Homónimos, números múltiples y accesos de app repetidos; cero despachos. |
| Preparación/cancelación | Estado compartido de Viaje, burbuja y notificación. | Silencio en cada etapa; ningún callback abre una captura después de cancelar. |
| Tono/idioma real | Reporte de Pruebas: señales, cancelación, idioma solicitado y último informado. | Comparar con lo oído y el resultado externo. |

La UI no decide ni ejecuta reglas de voz al componerse. Se conserva paleta, vidrio, contraste, iconos y navegación de la rama de diseño. El reconocimiento regional se determina por los modelos informados por Android; la región del número telefónico conserva su resolución anterior.

## Regresión y promoción

Ejecutar [REGRESION_FASE_1_RC1.md](REGRESION_FASE_1_RC1.md) y completar [regresion/F1_RC1_RESULTADOS.csv](regresion/F1_RC1_RESULTADOS.csv). Registrar la versión, modo, casco, sesión y resultados observados. Los resultados vacíos no son aprobaciones.

Promover esta baseline después de dos sesiones independientes completas en voz y botón sin acciones erróneas, aperturas tardías ni cierres inesperados; los fallos de reconocimiento recuperables deben quedar medidos. Validar canciones reales en ambos idiomas, cancelaciones, permisos, llamadas y recuperación musical. Identificar cualquier limitación del motor antes de anunciar reconocimiento bilingüe estable. `main` no se modifica en esta entrega.

## Validación automatizada

Validación final del código: `testDebugUnitTest`, `assembleDebug`, `assembleFieldTest` y `lintDebug` completaron **BUILD SUCCESSFUL**. **143 pruebas JVM**, cero fallos y errores; lint sin errores (74 advertencias y 9 sugerencias), con la dependencia de voz real. Recursos XML, `git diff --check` e integridad ZIP de ambos APK correctos. Se conservan advertencias del prototipo.

Las cinco comprobaciones nuevas cubren los límites de pantalla, densidad y texto de la burbuja. Las pruebas de RC1 cubren selección de idiomas según modelos, degradación conservadora, reglas de frases, protección de confirmaciones sensibles y cancelación de respuestas tardías. No sustituyen audibilidad, enrutamiento físico, precisión STT ni efecto final en apps externas. La tarjeta nueva requiere revisión visual en el teléfono; no se dispone de una pantalla Android conectada en este entorno.

Fuentes técnicas: [RecognizerIntent](https://developer.android.com/reference/android/speech/RecognizerIntent), [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer). La Especificación V4, Documento 02 y Documento 04 conservan el roadmap y sus límites.

