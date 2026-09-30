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

APP elige ahora la ruta de comunicación relacionada con la salida multimedia observada cuando Android la informa, incluida una ruta LE Audio si es la que corresponde. Si hay varios cascos conectados y la elección es ambigua, no se elige uno arbitrariamente. El diagnóstico muestra qué rutas Bluetooth de entrada están disponibles; la prueba de micrófono confirma la entrada real. APP no promete calidad alta solo porque aparezca LE Audio.

## Protocolo estacionario

1. Conectar **un casco por vez**. Repetir con Y12 Pro y con cada Skullcandy.
2. Usar el mismo teléfono, reproductor, canción y nivel multimedia. Anotar también el nivel propio del casco, si existe.
3. Antes de iniciar, capturar «Salida música prevista» y «Entradas casco disponibles» en Pruebas y diagnóstico.
4. Iniciar Modo Viaje con «Casco para activación y orden». Esperar a que indique «Esperando Alexa» y anotar «Ruta comunicación», «Salida música prevista», Música y Comunicación.
5. Con la sesión terminada y la moto detenida, ejecutar la medición de cinco segundos y anotar «Entrada real». Probar varias activaciones y una orden sencilla, comprobando en el reproductor si se ejecutó.
6. Comparar intensidad y claridad percibidas antes, durante y después; registrar si aparece LE Audio en la salida durante la espera o si sigue SCO. Detener y verificar que la música continúa sonando.

Si los Skullcandy también muestran SCO, la limitación continúa para la escucha permanente desde el casco. Si muestran LE Audio en salida **y** entrada real, se evaluarán calidad, intensidad y reconocimiento antes de llamar estable a esa ruta.

## Ajustes del Galaxy a comprobar sin cambiar APP

- Verificar en **Ajustes → Sonidos y vibración → Volumen → Límite de volumen multimedia** si hay un límite activo. Comparar solo si efectivamente está habilitado; 15/15 por sí solo no demuestra que exista.
- Probar **Adapt Sound** o ecualizador del Galaxy con una única configuración por vez y comparar especialmente durante SCO. Estos efectos dependen del modelo y la ruta; no se asume que compensen el cambio de perfil.
- Comprobar si el casco tiene un nivel de volumen propio separado. No desactivar protecciones ni elevar la salida automáticamente durante la conducción.

Fuentes técnicas: Android Developers, perfiles Bluetooth y grabación LE Audio; API de AudioManager, LoudnessEnhancer y AudioEffect. Samsung Support, límite de volumen multimedia y ajustes avanzados de sonido.
