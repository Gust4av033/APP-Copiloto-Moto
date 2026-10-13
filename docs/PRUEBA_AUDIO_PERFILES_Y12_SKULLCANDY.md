# Comparación de audio Bluetooth — Y12 Pro y Skullcandy

## Punto de partida

En el S22+ con Y12 Pro, al esperar «Alexa» APP observó salida de música Bluetooth SCO, frente a A2DP antes del viaje. Los niveles numéricos no bajaron; el usuario percibió menos intensidad y calidad. El cierre de sesión ya recuperó el sonido por voz, notificación y botón.

La entrada del teléfono oye en silencio, pero no resulta usable para activar APP durante la conducción. La pulsación larga del casco es un respaldo; el usuario no quiere depender de mantenerla presionada en ruta.

## Vías comprobables

| Vía | Qué puede aportar | Límite |
| --- | --- | --- |
| LE Audio bidireccional, si Android y el casco lo ofrecen | Entrada Bluetooth y salida de mayor calidad que SCO clásico | «LE Audio disponible» no confirma selección, nivel acústico ni precisión de Alexa; medir entrada y salida reales |
| Bluetooth clásico SCO del Y12 Pro o Skullcandy | Activación continua usando el micrófono del casco | La música puede pasar al perfil de voz; subir el número de volumen no recupera A2DP |
| Micrófono del teléfono con música A2DP | Comparación estacionaria de calidad | En ruta ya se comprobó que el teléfono no oye de forma útil |
| Pulsación larga del casco | Respaldo con música normal entre órdenes | No se plantea como control principal mientras conduce |
| Volumen propio del casco, límite multimedia y ajustes de sonido del Galaxy | Podrían cambiar la intensidad percibida según el dispositivo y el perfil | Se prueban por separado con la moto detenida; no se modifican automáticamente en APP |
| Efecto de ganancia desde APP | Útil para audio reproducido por la propia APP | La música la reproduce otra app y su sesión de audio no pertenece a APP; el efecto global sobre la mezcla está obsoleto |

APP elige ahora la ruta de comunicación relacionada con la salida multimedia observada cuando Android la informa, incluida una ruta LE Audio si es la que corresponde. Si hay varios cascos conectados y la elección es ambigua, no se elige uno arbitrariamente. El diagnóstico muestra qué rutas de comunicación Bluetooth ofrece Android; la prueba de micrófono confirma la entrada real. APP no promete calidad alta solo porque aparezca LE Audio.

## Protocolo estacionario

1. Conectar **un casco por vez**. Repetir con Y12 Pro y con cada Skullcandy.
2. Usar el mismo teléfono, reproductor, canción y nivel multimedia. Anotar también el nivel propio del casco, si existe.
3. Antes de iniciar, capturar «Salida música prevista» y «Rutas casco disponibles» en Pruebas y diagnóstico.
4. Iniciar Modo Viaje con «Casco para activación y orden». Esperar a que indique «Esperando Alexa» y anotar «Ruta comunicación», «Salida música prevista», Música y Comunicación.
5. Con la sesión terminada y la moto detenida, ejecutar la medición de cinco segundos y anotar «Entrada real». Probar varias activaciones y una orden sencilla, comprobando en el reproductor si se ejecutó.
6. Comparar intensidad y claridad percibidas antes, durante y después; registrar si aparece LE Audio en la salida durante la espera o si sigue SCO. Detener y verificar que la música continúa sonando.

Si los Skullcandy también muestran SCO, la limitación continúa para la escucha permanente desde el casco. Si muestran LE Audio en salida **y** entrada real, se evaluarán calidad, intensidad y reconocimiento antes de llamar estable a esa ruta.

## Prueba del volumen y cambio de casco (2026-09-29)

Con la moto detenida, poné música a un nivel medio, por ejemplo 7/15, antes de iniciar Modo Viaje. No comiences en 15/15: una orden de subir no puede superar el máximo. Al decir «Alexa, bajar volumen» y luego «Alexa, subir volumen», APP debe mostrar el nivel multimedia anterior y posterior, además del nivel de comunicación. La orden modifica música, incluso cuando el casco funciona en SCO; no sube automáticamente el volumen de llamadas ni amplifica el audio de otra aplicación. Si el número de música cambia pero la intensidad en el casco no cambia, anotá «cambia nivel, no sonido»: es un resultado distinto de «Android no cambió el nivel». Repetí fuera de Modo Viaje con la prueba de orden corta.

Para comparar Y12 Pro con Skullcandy, detené primero la sesión, elegí la salida de música en el panel de Android y volvé a iniciar. En «Rutas casco disponibles», «Ruta comunicación» y «Salida música prevista» anotá nombre, perfil e ID temporal mostrado por APP. Si ambos cascos están conectados y Android no informa una correspondencia inequívoca, APP debe evitar escoger uno arbitrariamente; desconectá el otro para esa prueba. Si se pierde la ruta del casco durante la sesión, APP la detiene para no pasar a un micrófono distinto sin aviso. La tarjeta **Dispositivos de audio** de Base UI permite elegir un casco explícito para el viaje y la medición, o mantener Automático. La salida musical se elige en Android: una ruta de voz puede cambiar también la salida y no garantiza combinaciones cruzadas A2DP/SCO. La orden corta fuera del viaje sigue usando la entrada elegida por Android.

Registrar para cada casco: salida elegida por Android; ruta de comunicación e ID; entrada real de la prueba de cinco segundos; música antes/durante/después; comunicación antes/durante/después; nivel tras cada orden; variación audible (sí/no); claridad (1–5); activaciones y órdenes correctas (de diez). La prueba de cinco segundos verifica su propia grabación, no sustituye la observación de Alexa durante la sesión.

## Cuando Skullcandy no aparece en «Elegir casco»

«Elegir casco» enumera únicamente las rutas Bluetooth que Android permite seleccionar para comunicación (SCO o LE). Un dispositivo puede estar conectado para música A2DP y faltar en esa lista. En **Dispositivos de audio → Actualizar dispositivos**, comparar las tres lecturas: cascos elegibles para voz, salidas Bluetooth detectadas y micrófonos Bluetooth detectados. La salida de música prevista representa solo la ruta actual de reproducción, no todos los dispositivos conectados.

Si Skullcandy aparece como salida y no como entrada, comprobar en los ajustes Bluetooth del Galaxy que esté conectado también para **Llamadas**. Si aparece como entrada pero no en «Elegir casco», Android no lo ofrece a `setCommunicationDevice` en ese momento: desconectar temporalmente el Y12 Pro, volver a conectar Skullcandy, actualizar y repetir. Si no aparece en ninguna lista, verificar su conexión en Android. APP no añade una salida A2DP al selector de voz como si fuera un micrófono; registrar las tres lecturas y el resultado del panel multimedia para identificar qué perfil expuso el S22+.

## Ajustes del Galaxy a comprobar sin cambiar APP

- Verificar en **Ajustes → Sonidos y vibración → Volumen → Límite de volumen multimedia** si hay un límite activo. Comparar solo si efectivamente está habilitado; 15/15 por sí solo no demuestra que exista.
- Probar **Adapt Sound** o ecualizador del Galaxy con una única configuración por vez y comparar especialmente durante SCO. Estos efectos dependen del modelo y la ruta; no se asume que compensen el cambio de perfil.
- Comprobar si el casco tiene un nivel de volumen propio separado. No desactivar protecciones ni elevar la salida automáticamente durante la conducción.

Fuentes técnicas: Android Developers, perfiles Bluetooth y grabación LE Audio; API de AudioManager, LoudnessEnhancer y AudioEffect. Samsung Support, límite de volumen multimedia y ajustes avanzados de sonido.
