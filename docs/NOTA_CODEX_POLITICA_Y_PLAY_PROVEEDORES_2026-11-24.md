# Nota para Codex — política y Play de proveedores de mapa

Inicio obligatorio de la Etapa 4: 24-11-2026.

## Estado final pendiente

Esta rama migra el mapa interactivo de teselas raster OSM a un proveedor
vectorial OpenFreeMap activo. La integración todavía requiere validación;
OSM queda documentado como rollback inactivo. No se distribuye ni publica.

## Datos enviados

- Host exacto: `tiles.openfreemap.org`.
- URL TileJSON: `/planet`.
- Teselas PBF versionadas: `/planet/<fecha>_<hora>_pt/{z}/{x}/{y}.pbf`.
- Teselas raster naturales: `/natural_earth/ne2sr/{z}/{x}/{y}.png`.
- Sprites: `/sprites/ofm_<id>/ofm(.json|.png)`.
- Tipografías: `/fonts/{fontstack}/{range}.pbf`.
- Solo HTTPS, GET, host exacto, puerto 443, zoom y patrón válidos.
- Sin query, fragmento, credenciales ni redirecciones.
- El User-Agent identifica APP, versión y enlace de código.
- Caché privada del SDK: 64 MiB; prefetch apagado.

El proveedor puede recibir IP y números de teselas; puede inferirse el área
vista. APP no envía trazo completo, historial, perfil de moto, contactos ni
audio. La autorización cambia a la versión 2: una aceptación de OSM versión 1
no habilita OpenFreeMap y queda en "sin calles" hasta reconsentimiento.

## Tareas de Codex

1. Añadir una adenda puntual a `privacy-policy.html` y a su resumen inglés.
2. Mantener `docs/PLAY_STORE_DECLARACIONES.md` como histórico y añadir la
   nueva matriz, sin renumerar ni reescribir secciones.
3. Incorporar hosts, caché y consentimiento a Data Safety y Play Console.
4. Verificar licencias, atribución y textos en Acerca de.
5. Contrastar los manifiestos fusionados Full/Play.
6. Confirmar que la política publicada coincida con el flujo real antes de Play.

Open-Meteo no se implementó en esta fase y no forma parte de esta adenda hasta
completar la Etapa 5.

## Bloqueos

No subir a Play ni distribuir esta rama hasta que estas tareas sean completadas
y aprobadas por el propietario.

## No verificable en esta entrega

- Red real con teselas públicas.
- Política publicada por el propietario.
- Capturas reales del S22+.
- Configuración y aprobación de la consola de Play.
- Disponibilidad futura o SLA de OpenFreeMap.

## Decisión pendiente del propietario

Enmendar la regla de `AGENTS.md` sobre mantener el navegador al frente durante
ruta cuando exista un motor de ruta propio. Esta rama no implementa rutas,
giro a giro, tráfico, ETA vial ni recálculo.
