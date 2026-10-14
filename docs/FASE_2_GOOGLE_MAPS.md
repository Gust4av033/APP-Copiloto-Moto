# Fase 2 · Google Maps como segundo proveedor

## Alcance

Versión `1.1.0-f2-maps1` (código 8), basada en `feature/fase-2-waze-screen` `15888f1`. Ajustes → Navegación permite elegir Waze o Google Maps; Waze conserva el valor inicial de instalaciones existentes. La selección se cambia con el viaje detenido y queda en preferencias locales. La pantalla Inicio, Comandos y Viaje muestran el proveedor de la solicitud real. Burbuja y notificación siguen mostrando el estado de APP y sus solicitudes pendientes; no afirman que una ruta externa esté activa.

«Busca [lugar]» usa el proveedor elegido. «Busca en Waze [lugar]» y «Busca en Google Maps [lugar]» lo indican explícitamente. APP abre el buscador del paquete elegido; el usuario selecciona el resultado con la moto detenida. No hay apertura silenciosa de otra app, navegador web o tienda si falta el paquete.

Casa y Trabajo conservan los favoritos propios de Waze cuando este está elegido. «Vamos a casa en Waze» y «Vamos a la chamba en Google Maps» también pueden indicar proveedor. Google Maps usa direcciones escritas explícitamente en APP para Casa y Trabajo; sin una dirección configurada, la orden explica el requisito y no despacha nada. Estas direcciones permanecen en la instalación y no se sincronizan; `allowBackup=false`. La solicitud `google.navigation:q=` incluye la dirección codificada y queda restringida al paquete de Maps. APP no confirma qué destino o ruta muestra Maps.

«Último destino» conserva por separado el último nombre Casa/Trabajo despachado a cada proveedor. Una búsqueda no lo reemplaza; cancelar o fallar una apertura tampoco. Al repetir en Maps se usa la dirección que tenga configurada ese nombre en ese momento. Borrar la última solicitud afecta solo al proveedor elegido. El contexto temporal del viaje registra `providerId` y se limpia al detenerlo; no almacena una ruta activa.

La lectura experimental de próximo giro corresponde solo a Waze. Google Maps no proporciona a APP giro, ETA ni distancia de ruta en esta entrega. No se añaden permisos Android, sensores ni llamadas a servicios externos. Las acciones conservan confirmación, locución, cancelación y recuperación de audio existentes.

## Prueba en el S22+

1. Sin viaje activo, elegir Google Maps en Ajustes → Navegación y guardar direcciones de prueba inequívocas para Casa y Trabajo. Verificar disponibilidad de Maps.
2. Iniciar viaje y solicitar Casa, cancelar con dos: no debe cambiar la última solicitud. Repetir y confirmar con uno: Maps debe quedar al frente y mostrar la dirección esperada; APP solo debe decir que envió la solicitud.
3. Solicitar «Busca gasolinera» y confirmar: elegir un resultado con la moto detenida. La última solicitud Casa/Trabajo debe conservarse. Probar «Busca en Waze gasolinera» con Maps seleccionado para comprobar la elección explícita.
4. Solicitar último destino, detener e iniciar otro viaje, repetir y comprobar que conserva el nombre del proveedor, sin reutilizar un contexto temporal de ruta. Probar Maps ausente o deshabilitado: no abrir otra app.
5. Alternar a Waze con viaje detenido y verificar que sus favoritos y última solicitud siguen independientes. Comprobar bloqueo, voz/botón, música y burbuja con cada navegador al frente.

## Verificación en este entorno

Las pruebas JVM y `assembleDebug` terminaron sin errores: 232 casos aprobados y uno omitido. La prueba de interfaz renderizada mostró la selección Waze/Google Maps y el editor de dirección a 150 % de tamaño de fuente. La dependencia real `openwakeword-android` no pudo descargarse desde JitPack por el proxy de este entorno; para ejecutar esas comprobaciones se usó temporalmente un stub de compilación local y se restauró la dependencia real en Gradle antes de entregar el cambio. El APK generado con ese stub no es instalable para pruebas de producto. Android Studio debe volver a compilar con la dependencia real.

La prueba física queda pendiente. Los Intents prueban despacho a un paquete, no inicio de navegación ni interpretación del destino por el proveedor.
