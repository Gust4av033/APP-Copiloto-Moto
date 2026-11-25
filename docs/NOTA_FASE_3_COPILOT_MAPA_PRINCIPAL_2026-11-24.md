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

- Validar compilación y tests después de los cambios vectoriales.
- Añadir edificios 3D con ajuste persistente todavía apagado.
- Realizar red real, atribución visible y smoke nativo.
- Completar Mi Moto o documentar el bloqueo real de las pruebas en Windows.
- Implementar Etapas 1, 2, 3 y 5 solo después de validar esta integración.
