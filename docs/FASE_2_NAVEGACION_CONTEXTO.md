# Fase 2 · Contexto mínimo de navegación

## Base consolidada

Inicio: 13 de octubre de 2026. PR #13 integrado en RC1 mediante d82c017; PR #14 integrado en main mediante 506a604. La rama feature/fase-2-navigation-context parte de ese mismo commit. El usuario confirmó que la versión instalada cumple las funcionalidades esperadas hasta la fecha. Esta aceptación no sustituye un registro individual de casos, sesiones, casco ni identificador de APK.

## Primera entrega

Crear un contexto de navegación para la sesión actual de APP. Separar la solicitud a Waze del estado real de su ruta. Actualmente NavigationProvider permite favoritos y búsquedas, pero no ofrece lectura de contexto ni detención de navegación.

El contexto debe incluir proveedor, tipo de solicitud (favorito o búsqueda), destino o consulta y momento del despacho. El despacho se registra únicamente tras una apertura aceptada. Una búsqueda no confirma el lugar finalmente elegido en Waze. Cancelar una confirmación no registra una nueva solicitud enviada.

Conservar separado el último favorito persistente, utilizado por el comando de repetición, y el contexto temporal del viaje. Al terminar la sesión, invalidar el contexto temporal sin borrar ese favorito. Una sesión nueva no hereda una ruta activa de la anterior.

No afirmar «Navegando» tras abrir Waze. Presentar «Solicitud enviada a Waze» y «Estado de ruta no disponible». Reservar ETA, distancia y próximo giro para observaciones reales con origen y vigencia; ninguna estimación se presenta como lectura del navegador.

## Trazabilidad de implementación prevista

| Capacidad | Integración | Comprobación |
| --- | --- | --- |
| Contexto por sesión | Estado compartido del viaje y ciclo de vida del servicio | Detener/iniciar elimina solicitudes de la sesión anterior |
| Solicitud despachada | Caminos de apertura en ActionExecutor y TripVoiceService | Favorito y búsqueda se registran después del despacho; cancelación/fallo no registran envío |
| Presentación | Estado de UI y pantalla Viaje; reutilizar componentes glass | Solicitud visible y ruta desconocida explícita, sin activar escucha al dibujar |
| Coherencia de sesión | Burbuja y notificación conservan el estado de APP | Viaje activo no se interpreta como navegación activa |
| Repetición | NavigationRequestStore sigue conservando el último favorito | El contexto temporal no altera la compatibilidad de Casa/Trabajo/último |

Los comandos actuales, permisos y ajustes se mantienen en esta entrega. Las nuevas consultas habladas de ETA/giro requieren antes una fuente validada. El despacho conserva el feedback hablado existente y su recuperación de audio.

## Criterios de aceptación

1. Casa/Trabajo y búsqueda actualizan la solicitud de esta sesión sin certificar una ruta.
2. Cancelación o fallo de apertura no crean un despacho nuevo.
3. Detener el viaje y empezar otro elimina el contexto anterior; permanece el último favorito para repetición.
4. Ruta, ETA, distancia y giro ausentes no se rellenan con datos ficticios.
5. Pantalla, burbuja y notificación no contradicen el estado de sesión ni interfieren con Waze.
6. Compilación y pruebas pertinentes pasan; revisión visual y regresión física se declaran por separado.

## Siguiente hito

Comprobar una fuente real de contexto de Waze en el S22+ antes de habilitar lectura de ETA, distancia o giros. Después añadir consultas por voz. Otros navegadores y anuncios automáticos se evalúan en entregas posteriores.

## Implementación Contexto 1

Versión 1.1.0-f2-context1 (código 4). TripNavigationState conserva un contexto observable en memoria con identificador nuevo por sesión. Un despacho incluye proveedor, tipo, texto solicitado y fecha. ActionExecutor registra después de abrir desde Activity; el servicio registra la apertura directa en segundo plano y evita duplicar el registro delegado a Activity. Se rechazan registros identificados con una sesión anterior. El cierre del servicio limpia el contexto; la muerte del proceso no persiste una ruta activa.

Viaje muestra la tarjeta «Navegación de este viaje» durante la sesión. Reutiliza SectionCard y SupportingText. No añade permisos, comandos, ajustes ni capturas de voz; conserva feedback hablado y recuperación de audio. Burbuja y notificación conservan estados de APP, sin afirmar navegación real. El favorito persistente permanece al detener el viaje; una búsqueda no lo sustituye.

### Prueba física de esta entrega

Con la moto detenida, instalar la versión identificada y comenzar viaje. Confirmar Casa/Trabajo y revisar la tarjeta al regresar a APP; debe mostrar solicitud enviada y ruta no disponible. Buscar un lugar y comprobar que solo refleja la consulta, no el resultado elegido. Cancelar otra solicitud y comprobar que no reemplaza la anterior. Detener/iniciar y verificar tarjeta vacía, conservando el comando del último favorito. Repetir por botón y voz y comprobar recuperación musical, burbuja, bloqueo y texto grande. Estos resultados físicos quedan pendientes hasta ser reportados.

## Validación automatizada

El 13 de octubre de 2026 se completaron testDebugUnitTest, assembleDebug y lintDebug con JBR 21/Gradle 8.13: BUILD SUCCESSFUL. 205 casos: 204 aprobados, uno omitido, cero fallos y errores. Lint: cero errores, 109 advertencias y nueve sugerencias. Las cinco pruebas nuevas de contexto cubren aislamiento de sesiones, conservación del favorito, búsquedas, aperturas fallidas y solicitudes independientes de un viaje. La prueba nueva de Compose verifica el texto de la tarjeta y la renderiza con fuente al 150 % en Android JVM. No equivale a una captura del S22+ ni a validar Bluetooth.

![Tarjeta renderizada en Android JVM con fuente al 150 %](images/fase_2_contexto_busqueda_150.png)

APK debug generado y estructura ZIP verificada (manifiesto y classes.dex presentes). SHA-256: 28382EC029F05BCC651CEBA385FADA3DE415FD0BB16060F4D2C420117C33497E.

assembleFieldTest también terminó BUILD SUCCESSFUL. La copia de pruebas mantiene el identificador .pruebas y ahora se presenta como APP · Campo F2. Actualizar con la misma firma conserva sus datos y permisos; debug actualiza la instalación normal con su identificador previo.

La validación final se ejecutó en el clon original de Android Studio. Un intento previo de dirigir la salida a D desde C falló por raíces distintas; el siguiente intento en un clon de D se interrumpió por demora al escribir archivos generados. Ninguno se contabiliza como aprobado. No se sustituyeron dependencias ni se descartaron archivos locales. Release no fue revalidado en esta entrega.

## Aceptación e integración

El 13 de octubre de 2026 el usuario confirmó que los pasos propuestos funcionan y autorizó proseguir. Las dos capturas del teléfono muestran sesión activa en modo botón, solicitud a Casa y tarjeta «Navegación de este viaje» con ruta desconocida. No se inventan resultados individuales ni mediciones. PR #15 integrado en main mediante 7a0c239, conservando ascendencia.

Estado: Contexto 1 aceptado por el usuario e integrado. La siguiente entrega recoge evidencia de Waze: [Observación experimental](FASE_2_OBSERVACION_WAZE.md).

## Consulta hablada de la solicitud actual · NavQuery 1

En `feature/ui-storm-refined`, versión `1.1.0-f2-navquery1` (código 13),
«¿Qué destino pedí?», «¿Qué búsqueda hice?» y «¿A dónde voy?» consultan el
último despacho de APP en el viaje actual. La respuesta distingue una solicitud
de Casa/Trabajo de una búsqueda, nombra el proveedor que recibió el Intent y
aclara que APP no sabe si la ruta está activa ni qué resultado se eligió. Sin
viaje o sin despacho da una respuesta explícita. Cambiar el proveedor preferido
no reescribe la solicitud ya enviada; detener/reiniciar descarta el contexto.

No lee Maps ni Waze, no inicia navegación, no añade permisos y no altera las
direcciones opcionales de Maps. Comandos y Ajustes anuncian la frase disponible;
Viaje ya muestra la misma solicitud. Burbuja y notificación mantienen el estado
de sesión y la locución reutiliza la recuperación de audio existente.

Las suites `fullDebug` y `playstoreDebug` terminaron con 249 casos cada una,
cero fallos y dos y tres omisiones respectivamente. Las pruebas nuevas cubren
frases de consulta sin apertura, proveedor real del despacho, búsqueda y
aislamiento tras reiniciar el viaje. Es una comprobación en Android JVM.
`assembleFullDebug`, `assemblePlaystoreDebug`, `assemblePlaystoreRelease` y
`processPlaystoreReleaseManifest` terminaron correctamente. Los dos APK debug
firmados y el release sin firmar muestran versión 13; este último necesita la
firma de publicación antes de instalarse o subirse a Play.

Prueba física pendiente: con el casco, pedir Casa, consultar la solicitud y
confirmar que se nombra el proveedor correcto sin afirmar navegación activa.
Repetir tras una búsqueda, tras cambiar el proveedor y tras reiniciar el viaje;
comprobar que la música y el modo botón regresan normalmente.
