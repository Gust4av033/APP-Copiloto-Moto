# Prueba de salida 02 búsqueda de lugares y revisión de mensajes

Estos dos incrementos amplían lo que se puede probar en la próxima salida: buscar un lugar en Waze y corregir o volver a escuchar un borrador de WhatsApp. La revisión visible es **Prueba de salida 02**. No se modifica el motor de dictado ni se afirma haber calibrado su reconocimiento en ruido.

## Búsqueda de lugares en Waze

- Activar APP como siempre, esperar la señal y decir **«Busca en Waze gasolinera»**, **«Busca gasolinera en Waze»** o **«Busca en Waze [nombre o dirección]»**.
- APP anuncia la búsqueda. Después de la señal, uno la abre y dos cancela, con la misma ventana y política de apertura que las apps y los favoritos.
- Waze recibe un enlace oficial de búsqueda de texto. Muestra/resuelve sus resultados; APP no lee la lista ni escoge un resultado por voz. La selección se hace en Waze con la moto detenida.
- El texto conserva acentos y se codifica como un único parámetro de búsqueda. Se admite hasta 160 caracteres; no se ejecutan órdenes combinadas. El Intent se dirige sólo a Waze y no abre una tienda ni otro navegador.
- Buscar no reemplaza la referencia **último destino** de APP: sigue repitiendo la última solicitud de Casa/Trabajo. APP no conoce qué resultado se eligió dentro de Waze.

Prueba breve: buscar una gasolinera, cancelar; repetir y abrir; pedir un nombre concreto con acentos; comprobar recuperación musical y continuidad del viaje. Con Waze al frente siguen aplicando teléfono desbloqueado y Mostrar sobre otras apps. Sin proveedor o sin permiso, comprobar el mensaje/aviso, sin asumir que una búsqueda enviada equivale a un lugar encontrado.

## Revisión del borrador por voz

Durante la confirmación de un borrador, después de su lectura y la señal:

| Respuesta | Acción |
|---|---|
| Uno | Abre WhatsApp con el borrador actual |
| Dos | Descarta la solicitud |
| Tres / Dictar de nuevo / Corregir mensaje | Descarta el texto anterior e inicia otra captura para el mismo contacto y edición de WhatsApp |
| Cuatro / Repetir mensaje / Lee de nuevo | Relee el borrador y vuelve a ofrecer las opciones |

Estas respuestas se interpretan sólo al revisar un borrador vivo. Al dictar el mensaje, «tres», «repetir» y otras palabras forman parte de su contenido. En la confirmación de una app, ruta o búsqueda normal, tres/cuatro no ejecutan estas acciones.

Volver a dictar invalida el aviso/token anterior antes de abrir la captura y elimina el texto del enlace antiguo. Conserva contacto, número y edición ya resueltos; no vuelve a buscar otra persona. Se espera a que termine el anuncio y el reconocedor esté listo para emitir la señal. El nuevo texto se valida, se lee y recibe una nueva solicitud/token.

Repetir la lectura reserva también el tiempo de locución, para que un borrador largo no venza mientras se relee. Los plazos de cada anuncio/captura y los mecanismos de cancelación/expiración permanecen; no hay relecturas automáticas ni acciones externas sin una confirmación explícita.

Uno abre un texto prellenado; el envío sigue siendo manual en WhatsApp. Si se pierde o cancela el segundo dictado, el texto anterior ya está descartado. Esta es una sustitución completa, no una edición por palabras.

Prueba breve, estacionaria:

1. Pedir **«Escribe a [contacto único] en WhatsApp mensaje voy llegando»**.
2. Después de lectura/señal, decir cuatro. Verificar que se oye el mismo mensaje y aparece otra señal.
3. Decir tres. Esperar anuncio/señal, dictar **«Llegaré en diez minutos»** y comprobar que la nueva lectura no contiene «voy llegando».
4. Decir uno y revisar contacto y texto en WhatsApp. Repetir con dos para comprobar descarte.
5. Durante relectura o nuevo dictado, detener/cancelar desde el aviso. El aviso antiguo no debe abrir el texto anterior y el micrófono/música deben recuperarse.
6. Probar tres/cuatro al confirmar Waze u otra app: no redictar ni releer mensajes.

Para calibrar después el dictado, anotar si pierde el inicio, corta tras una pausa, omite el final o transcribe mal palabras. Estos incrementos permiten repetir la prueba y recuperarse del texto incorrecto; no sustituyen esa medición.

## Validación

`testDebugUnitTest assembleDebug lintDebug`: **BUILD SUCCESSFUL** con JDK 17, Gradle 8.13 y SDK 36. **107 pruebas JVM** sin fallos, errores u omisiones. Incluyen alcance de las respuestas tres/cuatro, preservación del texto dictado, enlaces sin contenido anterior, consultas codificadas sin inyección de parámetros y regresiones anteriores. APK debug generado y lint sin errores.

La recepción de enlaces en Waze/WhatsApp, las señales Bluetooth y los tiempos reales requieren pruebas en S22+/Y12. No se ha realizado la prueba física de esta entrega.

Referencia de búsqueda: [Waze Deep Links](https://developers.google.com/waze/deeplinks), sección Search. Los flujos no usan lectura de pantalla ni simulan pulsaciones en apps externas.
