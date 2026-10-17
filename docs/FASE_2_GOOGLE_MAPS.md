# Fase 2 · Google Maps como segundo proveedor

## Alcance

Versión actual `1.1.0-f2-maps2` (código 11) en `feature/fase-2-integrada`. La primera entrega Maps1 partió de `feature/fase-2-waze-screen` `15888f1`. Ajustes → Navegación permite elegir Waze o Google Maps; Waze conserva el valor inicial de instalaciones existentes. La selección se cambia con el viaje detenido y queda en preferencias locales. La pantalla Inicio, Comandos y Viaje muestran el proveedor de la solicitud real. Burbuja y notificación siguen mostrando el estado de APP y sus solicitudes pendientes; no afirman que una ruta externa esté activa.

«Busca [lugar]» usa el proveedor elegido. «Busca en Waze [lugar]» y «Busca en Google Maps [lugar]» lo indican explícitamente. APP abre el buscador del paquete elegido; el usuario selecciona el resultado con la moto detenida. No hay apertura silenciosa de otra app, navegador web o tienda si falta el paquete.

Casa y Trabajo conservan los favoritos propios de Waze cuando este está elegido. «Vamos a casa en Waze» y «Vamos a la chamba en Google Maps» también pueden indicar proveedor. Maps no ofrece a APP una API pública para leer las direcciones personales guardadas de Casa/Trabajo ni un enlace documentado equivalente a `favorite=home` de Waze. Una dirección escrita explícitamente en APP sigue siendo una opción para solicitar la ruta directa mediante `google.navigation:q=`. Si el campo está vacío, APP abre dentro del paquete de Maps la búsqueda `geo:0,0?q=Casa` o `Trabajo`: el conductor debe reconocer y elegir su lugar guardado con la moto detenida. La búsqueda puede presentar otros resultados y APP no confirma que Maps haya encontrado el favorito o iniciado una ruta. Las direcciones locales no se sincronizan; `allowBackup=false`.

«Último destino» conserva por separado el último nombre Casa/Trabajo despachado a cada proveedor como **solicitud de ruta directa**. Una búsqueda, incluida la búsqueda de Casa/Trabajo por falta de dirección, no lo reemplaza; cancelar o fallar una apertura tampoco. Al repetir en Maps se usa la dirección que tenga configurada ese nombre en ese momento; si se borró, se abre la búsqueda y no se registra un nuevo favorito confirmado. Borrar la última solicitud afecta solo al proveedor elegido. El contexto temporal del viaje registra `providerId` y si se despachó ruta o búsqueda; se limpia al detenerlo y no almacena una ruta activa.

La lectura experimental de próximo giro corresponde solo a Waze. Google Maps no proporciona a APP giro, ETA ni distancia de ruta en esta entrega. No se añaden permisos Android, sensores ni llamadas a servicios externos. Las acciones conservan confirmación, locución, cancelación y recuperación de audio existentes.

## Prueba en el S22+

1. Sin viaje activo, elegir Google Maps en Ajustes → Navegación y dejar Casa sin dirección local. Verificar disponibilidad de Maps y que la cuenta tenga Casa guardada. Iniciar viaje, pedir Casa y confirmar: debe abrir la búsqueda de Casa en Maps; elegir el lugar guardado solo con la moto detenida. APP debe decir búsqueda enviada, sin afirmar ruta ni guardar un último favorito.
2. Detener el viaje, guardar una dirección de prueba inequívoca para Casa y repetir. Cancelar con dos: no debe cambiar la última solicitud. Repetir y confirmar con uno: Maps debe quedar al frente y mostrar la dirección esperada; APP solo debe decir que envió la solicitud. El último favorito de Maps pasa a Casa.
3. Solicitar «Busca gasolinera» y confirmar: elegir un resultado con la moto detenida. La última solicitud Casa/Trabajo debe conservarse. Probar «Busca en Waze gasolinera» con Maps seleccionado para comprobar la elección explícita.
4. Solicitar último destino, detener e iniciar otro viaje, repetir y comprobar que conserva el nombre del proveedor, sin reutilizar un contexto temporal de ruta. Borrar la dirección local y repetir: debe abrir una búsqueda sin convertirla en último favorito. Probar Maps ausente o deshabilitado: no abrir otra app.
5. Alternar a Waze con viaje detenido y verificar que sus favoritos y última solicitud siguen independientes. Comprobar bloqueo, voz/botón, música y burbuja con cada navegador al frente.

## Verificación en este entorno

En Maps2 se ejecutaron 18 pruebas de proveedor, contexto y comandos y 19 pruebas de Ajustes y contraste: **37 aprobadas, cero fallos**. `assembleDebug` y `assembleFieldTest` terminaron correctamente después del cambio de textos. Se inspeccionó la pantalla de Navegación renderizada al 150 % de fuente; la explicación de la dirección opcional y el editor permanecen accesibles por scroll. El APK Campo `1.1.0-f2-maps2-campo` (código 11) contiene los cinco modelos ONNX reales, paquete `.pruebas` y la misma firma debug que Campo código 10. La dependencia real OpenWakeWord 0.1.2 se resolvió desde la compilación local de su etiqueta de GitHub; el repositorio mantiene JitPack y retiró la configuración temporal de Maven local. La suite completa de la base integrada ya aprobó 236 casos, omitió uno y tuvo cero fallos; Maps2 repitió las pruebas afectadas.

Historial de Maps1: las pruebas JVM y `assembleDebug` terminaron sin errores (232 casos aprobados y uno omitido) y la selección Waze/Maps se renderizó al 150 % de fuente. En ese entorno JitPack no respondió y se usó temporalmente un stub de compilación; aquel APK no era instalable para pruebas de producto. El APK de Maps2 descrito arriba sí incorpora la dependencia real.

La prueba física queda pendiente. Los Intents prueban despacho a un paquete, no inicio de navegación ni interpretación del destino por el proveedor.

## Actualización posterior de la rama UI integrada · 15 de octubre de 2026

El alcance descrito arriba corresponde a Maps2. La rama funcional actual
`feature/ui-storm-refined` observa notificaciones de Maps durante el viaje y
texto de su pantalla en la variante Full autorizada. El usuario confirmó una
ETA de 7 minutos en una notificación real. Inicio → Mi ruta y Viaje muestran
ahora los campos recientes que el parser pueda obtener, con fuente Google
Maps y sin afirmar que APP confirmó el destino o la ruta. Un aviso genérico
de Waze no tapa una observación útil de Maps. Véase
[contexto de navegación de Fase 2](FASE_2_NAVEGACION_CONTEXTO.md).
