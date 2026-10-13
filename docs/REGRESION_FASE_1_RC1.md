# Regresión repetible de Fase 1 RC1

## Preparación

Usar la misma biblioteca musical, nivel de volumen y contactos de prueba consentidos en cada pasada. Moto detenida para las pruebas que requieran pantalla o configuración. Anotar S22+ / Android, Y12 Pro, versión `1.0.0-rc1`, modo y fecha. Detener cualquier viaje en la otra instalación de prueba.

En Ajustes → Activación comprobar idiomas y, si corresponde, preparar inglés. Conservar permisos existentes de micrófono, contactos, teléfono, acceso multimedia y superposición por función. Guardar Casa/Trabajo en Waze. Tener dos títulos exactos en inglés y uno en español en Poweramp; una biblioteca vacía no mide reconocimiento.

Para probar homónimos/números múltiples, usar exclusivamente contactos de prueba identificados y borrar los datos de prueba al finalizar. La revisión de la agenda es local; APP no elige un contacto ambiguo.

## Dos modos, dos sesiones independientes

Realizar la matriz completa en **voz desde el casco** y **botón dedicado Anterior**. Detener/iniciar entre modos; repetir ambas pasadas en otra sesión. En botón activar Solo escuchar al pedir una orden. En voz conservar la espera de Alexa. Esperar el tono antes de hablar y que vuelva a estar lista antes del siguiente intento.

Completar una fila por ejecución en [F1_RC1_RESULTADOS.csv](regresion/F1_RC1_RESULTADOS.csv), usando PASA, FALLA o BLOQUEADO. Agregar filas cuando haya repeticiones. Guardar/copiar el diagnóstico al final de cada pasada y en cualquier fallo, indicando si ocurrió durante anuncio, preparación, escucha o restauración.

| ID | Acción | Resultado verificable |
| --- | --- | --- |
| R01 | Diez activaciones consecutivas con música, una orden cada vez. | Diez intentos contabilizados; sesiones fallidas recuperan espera; botón no se pierde. |
| R02 | Siguiente, anterior, pausa y reanudar. | Pista/estado correctos; una orden produce una acción. |
| R03 | Volumen a 6, al 40 %, subir/bajar; pedir nivel 40 sin porcentaje. | Cambios audibles/rango correcto; nivel inválido no ajusta. |
| R04 | «Pon [título inglés]», «reproduce [título español]», «play [título inglés]». | Texto reconocido y canción externa correctos; sin cambiar idioma ni pedir una segunda orden. |
| R05 | Comprobar idiomas presentes/ausentes. | UI honesta y español funcional; modelos preparados no se presentan como precisión certificada. |
| R06 | Pedir app conocida; confirmar uno tras 1 s y repetir tras 5 s. | Tono audible cuando escucha; abre solo la app actual, desbloqueada y con requisitos. |
| R07 | Repetir app con dos/«mejor no», silencio y vencimiento. | No abre; cancelación activa tiene señal diferenciada si la ruta permite; vuelve a esperar. |
| R08 | Pedir Casa, Trabajo, último destino y búsqueda de lugar. | Destino/búsqueda correctos en Waze; dos cancela; último solo usa solicitudes de APP. |
| R09 | «Abre el chat de [contacto] en WhatsApp». | Chat correcto sin texto prellenado; uno/dos funcionan. |
| R10 | Dictar mensaje corto; cuatro, tres, nuevo texto y uno. | Lectura completa antes del tono; texto nuevo correcto; envío manual en WhatsApp. |
| R11 | Borrador más largo (<240 caracteres); cancelar durante lectura/preparación/escucha. | No vence mientras se lee; ninguna respuesta vieja abre o recupera el borrador. |
| R12 | Pedir contacto homónimo y contacto con varios números. | Aclaración audible, instrucciones útiles y cero llamadas/chats despachados. |
| R13 | Pedir app inexistente o nombre con accesos repetidos. | Explicación audible y selección explícita; no adivina un acceso. |
| R14 | Llamada saliente: confirmar y terminar desde teléfono/casco. | Número consentido correcto; libera audio, recupera el mismo viaje y acepta otra orden. |
| R15 | Llamada: Silencio durante anuncio, justo al terminarlo y durante escucha. | No marca en ninguna etapa; la confirmación atrasada no sirve. |
| R16 | Música inicialmente pausada; cancelar app y borrador. | Conserva pausada. Con música inicialmente activa, recupera tras restaurar la ruta. |
| R17 | Otra app al frente y pantalla bloqueada. | Música/botón continúan; abrir app no desbloquea el teléfono ni confirma un efecto que no ocurrió. |
| R18 | Detener viaje con confirmación pendiente; iniciar otro. | Cero capturas/aperturas/llamadas tardías de la sesión anterior. |
| R19 | Ocultar/mostrar/mover burbuja y volver a Ajustes. | Conserva viaje; ningún cambio visual abre un micrófono. |
| R20 | UI día/noche, texto grande y estado de idiomas. | Tarjeta glass legible, controles identificados y descarga/comprobación sin captura de voz. |
| R21 | Abrir panel sobre Waze en espera, escuchando, hablando, confirmación, llamada y recuperación; minimizar con ×/fuera y ocultar. | Panel breve con estado real; apertura/arrastre no capturan audio, solo Pedir una orden; Ocultar conserva el viaje; controles fuera del panel siguen accesibles. |
| R22 | Con panel abierto, cambiar día/noche, orientar el teléfono y usar fuente 150–200 %; arrastrar cerca de cada borde. | Tarjeta dentro del espacio útil, acciones verticales cuando corresponde, texto legible, scroll accesible y Ocultar visible. Registrar captura nativa y dimensiones observadas. |

## Cómo leer fallos

- **Activación:** anotar si hubo intento y si APP lo recibió. En botón comparar `button_down_received` con `button_activation`; en voz, detecciones y falsos positivos observados.
- **Preparación:** si no hay READY/tono, revisar fase, entrada y ruta. Tiempo de preparación no es precisión STT.
- **Reconocimiento:** comparar texto oído con frase pronunciada. Idioma solicitado, detectado y cambio informado son evidencias diferentes. La ausencia de un callback no demuestra por sí sola incapacidad bilingüe.
- **Despacho:** una solicitud enviada no certifica la pantalla, canción, destinatario o ruta final. Confirmar el efecto mirando la app externa después de detenerse.
- **Audio:** anotar el tono realmente oído y la música después. En voz/SCO puede continuar calidad de comunicación; en botón debe volver a la ruta musical. Un PLAY solicitado o un tipo de ruta no prueban calidad audible.

## Criterio de candidata a estable

R06–R19 deben pasar sin acción equivocada, despacho después de cancelar, captura tardía ni cierre inesperado. Un resultado bloqueado por falta de permiso/modelo se registra y se repite después de configurarlo; no cuenta como aprobado. Medir aciertos/total en R01/R04 y registrar fallos recuperables sin inventar una tasa general de precisión.

Completar las dos sesiones en ambos modos, documentar las limitaciones encontradas y repetir los casos fallidos después de corregirlos. Hasta entonces mantener RC1 como candidata. El envío automático de WhatsApp, audio por voz y silencio pronunciado durante TTS no forman parte del alcance implementado de esta baseline y permanecen visibles en el backlog de [FASE_1_RC1.md](FASE_1_RC1.md).

