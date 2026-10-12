# Fase 2 navegación mínima con Waze

## Estado al 12 de octubre de 2026

El usuario reporta que las funciones actuales responden bien, con dictado de WhatsApp todavía poco calibrado. Este avance inicia Fase 2 según la especificación v4 y la revisión integral del 3 de octubre. El núcleo de Fase 1 queda como candidato a cierre, respaldado por el funcionamiento reportado, a falta de regresión final documentada y consolidación de una versión estable. Dictado/borradores sigue como adelanto de mensajería de Fase 5 y ajuste pendiente.

## Órdenes

| Orden en el viaje | Solicitud preparada |
|---|---|
| Vamos a casa / Llévame a casa | Favorito Casa guardado en Waze |
| Vamos al trabajo / Llévame al trabajo | Favorito Trabajo guardado en Waze |
| Vamos al último destino / Vamos a la última ubicación puesta | Último favorito solicitado desde APP |

Se aceptan variantes breves y «en Waze» al final. «Abre Waze» conserva su acción de abrir la app. No se interpretan lugares libres ni órdenes combinadas como «vamos a casa y llama a Ana» como navegación.

## Preparación y uso

1. Guardar Casa y Trabajo en Waze. APP no lee sus direcciones ni necesita GPS/accesibilidad para este avance.
2. Actualizar integraciones en Ajustes y comprobar Waze disponible. La revisión visible es **Navegación 01**, con tarjeta en Ajustes y Comandos.
3. Iniciar el viaje, activar APP como siempre y pedir un destino después de la señal.
4. Escuchar el destino y esperar la nueva señal. **Uno** solicita la ruta; **dos** cancela. Se reutilizan la confirmación de 18 segundos de la revisión 06, tokens y expiración.
5. Con Waze al frente, la apertura desde el servicio requiere teléfono desbloqueado y Mostrar sobre otras apps. Con APP visible se usa su Activity. Si no se permite abrir, se conserva el aviso y se explica el requisito; no se desbloquea Android.

Waze resuelve su favorito y decide el inicio de navegación. APP informa **solicitud enviada**, sin afirmar ruta calculada, favorito válido o destino confirmado. Un favorito no configurado debe corregirse en Waze.

## Último destino y privacidad

La API documentada ofrece favoritos Casa/Trabajo, sin lectura del historial. Sólo se guarda el identificador del favorito después de enviar su Intent. Cancelar, no confirmar, preparar una solicitud o un fallo al lanzarla no reemplaza la referencia.

La referencia persiste al reiniciar APP y se puede borrar con **Olvidar última solicitud de ruta**, sin viaje/captura activo. Si está vacía, APP pide solicitar Casa o Trabajo primero; no inventa un destino.

Se repite una solicitud al favorito, no sus coordenadas. Cambiar Casa/Trabajo dentro de Waze cambia la dirección que Waze resolverá. Un destino elegido manualmente en Waze no actualiza esta referencia. No se almacenan direcciones, GPS ni historiales externos.

## Arquitectura

El router produce `NavigationRequest` y `NavigationDestination` comunes. `ActionExecutor` prepara la apertura mediante `NavigationProvider`; `WazeNavigationProvider` contiene enlace y paquete. Sus capacidades declaran apertura de favoritos y marcan lectura de historial/contexto y detención de navegación como no soportadas. No se simulan ETA, giro, tráfico ni ruta activa.

El Intent queda restringido a `com.waze`. Sin Waze, se informa y recupera la espera, sin abrir navegador web, tienda ni otra app. `AppTarget` y el coordinador existente conservan cancelación, reemplazo, expiración y rechazo de respuestas antiguas. Google Maps y el proveedor externo genérico quedan para adapters posteriores, sin selector ficticio de proveedores.

## Prueba física

Con la moto detenida y favoritos de prueba conocidos:

1. Sin Waze/deshabilitado: estado no disponible, no abrir otra app y recuperar escucha/música.
2. Pedir Casa y decir dos: no cambiar ruta ni crear/reemplazar la última solicitud.
3. Pedir Trabajo y decir uno: comprobar destino en Waze, tono en Y12 y continuidad del viaje. Repetir con música, Waze al frente y overlay autorizado.
4. Pedir último destino, reiniciar APP y repetir. Debe usar el favorito solicitado desde APP; una elección manual en Waze no lo cambia. Borrar la referencia: no elegir alternativa.
5. No responder; cancelar desde aviso durante anuncio/escucha; detener viaje; reemplazar Casa por Trabajo: una confirmación anterior no debe ejecutar una solicitud anterior.
6. Bloquear durante confirmación: no desbloquear ni abrir automáticamente. Volver a APP y comprobar aviso/audio.
7. Regresión de Fase 1 en voz y botón: pulsaciones consecutivas con música, controles multimedia/volumen, canción por nombre, app con uno/dos, cancelar/silencio y continuidad tras llamada.

Dictado de WhatsApp queda en el backlog inmediato: medir pérdidas de inicio, pausas, final o transcripción, y validar lectura/cancelación con un mensaje corto antes de ajustar el motor. Esta entrega no modifica el dictado ni promete envío automático.

## Validación local

`clean testDebugUnitTest assembleDebug lintDebug` completó **BUILD SUCCESSFUL** con JDK 17, Gradle 8.13 y SDK 36. **98 pruebas JVM** en 23 suites: cero fallos, errores u omisiones. Incluyen frases de navegación, resolución sin destino previo, rechazo de frases combinadas y conservación de comandos existentes, enlaces de favoritos y regresiones anteriores. APK debug generado; lint sin errores (62 advertencias y 9 sugerencias). `git diff --check` correcto.

Reglas y compilación no sustituyen el ensayo de Waze/Bluetooth en S22+. No se ha verificado en un teléfono el destino resultante, el inicio de navegación, los tonos ni la recuperación musical de esta entrega.

Referencia: [Waze Deep Links oficial](https://developers.google.com/waze/deeplinks), Navigate to favorite. No se usa AccessibilityService ni lectura de pantalla.
