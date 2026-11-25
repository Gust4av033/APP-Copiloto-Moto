# Mapa principal y proveedor · avance Copilot

## Línea base

Rama creada: `feature/fase-3-mapa-principal-copilot`.
Base remota confirmada: `origin/feature/fase-3-viaje-mapa`, commit `917fd94`.
La rama conserva Mi Moto, el mapa MapLibre y el GPS compartido.

## Etapa 0 — resultado real

- La suite Full inició y mostró 2 fallos persistentes en
  `MotorcyclePhotoStoreTest` con el entorno local antes de completar el conjunto.
- Se volvió a ejecutar solo esa clase y confirmó los mismos fallos.
- Se intentó `GraphicsMode.LEGACY`: hizo pasar la eliminación privada, pero hizo
  fallar el rechazo de una imagen inválida y se retiró.
- No se conserva el cambio experimental; el código de Mi Moto volvió a la base.
- Play unit, ensamblados y lint no se completaron en esta sesión.

## Etapa 4 — investigación y avance

Investigación consultada el 24-11-2026:

- OpenFreeMap ofrece el servicio público sin clave y sin límite técnico declarado
  por la página consultada, sin SLA.
- Privacidad consultada el 24-11-2026: sin IP en logs normales, con IP temporal
  en errores/seguridad hasta 7/30 días según el caso.
- Términos consultados el 24-11-2026: uso responsable, no offline masivo y
  atribución visible.
- El único host identificado en estilo y TileJSON fue
  `tiles.openfreemap.org`.
- El estilo oficial Liberty fue descargado como asset local.
- Se añadió una variante derivada oscura para tormenta.
- OSM raster queda como proveedor de rollback inactivo, no como fallback.
- El consentimiento de mapa sube a versión 2. Una aceptación anterior de OSM no
  habilita el proveedor nuevo.

## Pendiente

- Ejecutar el conjunto completo Full/Play, ensamblados y lint después de la
  integración vectorial y de la vista principal.
- Añadir edificios 3D con ajuste persistente todavía apagado.
- Realizar red real, atribución visible y smoke nativo.
- Completar Mi Moto o documentar el bloqueo real de las pruebas en Windows.
- Implementar Etapas 3 y 5.

## Etapa 1 — mapa principal

Implementación realizada el 24-11-2026:

- Inicio pasa a presentar el mapa como superficie principal.
- Sin muestra GPS válida, muestra un estado vacío con la acción concreta:
  activar GPS y iniciar una sesión con la moto detenida.
- No existe un control de "ver mi ubicación" fuera de una sesión.
- El chip superior distingue GPS, sesión y estado de audio.
- El dock usa objetivos táctiles de 56 dp para Mi Moto, iniciar/detener Viaje
  y Ajustes.
- La hoja inferior conserva ayuda, ajustes, comandos y permisos sin iniciar
  captura ni GPS al abrirse.
- Se reutiliza `FullScreenTripMap` sobre `NativeTripMapController`; no se creó
  un segundo motor GPS ni un renderer duplicado.
- El mapa conserva BackHandler, navegación secundaria y el renderer existente.
- La prueba de Inicio fue migrada de carrusel a la superficie principal.

Validación real:

- Compilación Full principal y de tests: aprobada.
- Pruebas enfocadas de Inicio, política de mapa, sesión y UI: aprobadas.
- No se abrió micrófono, GPS ni servicio en las pruebas.
- Captura visual física, S22+, Y12 Pro, atajo del casco y red real: pendientes.

## Corrección de arranque y Etapa 2 — cámara

Corrección local de la parada automática al iniciar:

- Si Android aún no reporta una entrada activa `VOICE_RECOGNITION` durante el
  arranque Bluetooth, el proveedor ahora informa un fallo de verificación.
  El servicio reintenta hasta cuatro veces, con un segundo entre intentos; si
  sigue sin poder verificar el micrófono, conserva el cierre seguro existente.
- El dock de Inicio vuelve a respetar `canStartTrip`: no permite despachar
  Inicio de viaje mientras falte el casco seleccionado.
- No se modificaron permisos, GPS, llamadas, ciclo de audio, ni la sesión de
  burbuja/notificación. La vista solo refleja estado; no dispara el servicio.

Etapa 2 de cámara:

- Inicio y Viaje ofrecen tres modos seleccionables con estado accesible:
  **Rumbo**, **Norte** y **Libre**.
- Rumbo sigue la última posición aceptada y solo orienta el mapa con rumbo GPS
  reciente, velocidad de al menos 2 m/s y precisión de rumbo de hasta 30°.
  Sin rumbo válido, permanece norte arriba y lo indica explícitamente.
- Norte mantiene el seguimiento de posición con orientación norte arriba.
  Libre interrumpe el seguimiento y habilita el giro manual; un gesto de mapa
  cambia automáticamente a Libre. Ajustar el trazo también deja la cámara Libre.
  **Centrar última posición** restablece Rumbo.
- No se calcula una ruta vial ni se inicia una captura por cambiar la cámara.
  No requiere permisos nuevos y no cambia comandos, burbuja o notificación.

Validación local de esta entrega:

- `WakeActivationGateTest`, `HomeDashboardUiTest`, `TripMapPolicyTest` y
  `TripMapUiTest`: aprobados, incluida la selección visible de cámara, el filtro
  del rumbo y el bloqueo del dock cuando falta el casco seleccionado.
- `SettingsContrastTest`, `lintFullDebug` y las pruebas focalizadas Play de
  activación y mapa: aprobadas. Lint no encontró errores.
- Compilaciones Kotlin Full y Play: aprobadas. La prueba física del auto-stop con casco
  Bluetooth (S22+/Y12 Pro), giro real de cámara, TalkBack y capturas reales queda
  pendiente; las pruebas JVM no acreditan el comportamiento físico.
