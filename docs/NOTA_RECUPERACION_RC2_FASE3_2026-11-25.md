# Recuperación RC2 de Fase 3

Base: `e010717`, rama `feature/fase-3-recuperacion-rc2`.

## Estado implementado

- Cámara principal conserva la posición GPS y el trazo. Se agregan modos Rumbo, Norte y Libre, además de perspectiva 2.5D y vista 2D.
- El marcador conserva su proyección sobre el mapa y refleja el rumbo solo con muestra viva, velocidad de al menos 2 m/s y precisión de rumbo.
- Mi Moto conserva SQLite, foto privada, odómetro manual y mantenimientos. Se incorpora el catálogo local `Hero Ignitor 125` sin reemplazar datos existentes.
- Open-Meteo queda detrás de consentimiento independiente, caché privada, límite de vigencia, estado vencido y recuperación ante error o sin conexión.
- Se conserva MapLibre, OSM raster, Waze y Google Maps. OpenFreeMap se evaluó, pero no se activó en esta recuperación: este entorno obtuvo HTTP 403 y faltan validación binaria en dispositivo y atribución confirmada.

## Licencias y privacidad

- Natural Earth y MapLibre conservan sus licencias existentes.
- Open-Meteo requiere atribución CC BY 4.0 y puede recibir coordenadas e IP.
- No se incorporaron claves, precios asumidos, facturación ni proveedores de tráfico.
- No se habilita ruta vial propia, POI propio, semiconversación ni giro a giro.

## Validación

- Pruebas enfocadas RC2 aprobadas: catálogo, modos de cámara y umbrales de clima.
- APK Full Campo compilado con firma de pruebas.
- APK Play Debug compilado y empaquetado.
- Lint Full Debug y Play Debug aprobados, con advertencias existentes fuera de esta entrega.
- SHA-256 Campo RC2: `7690F3C79C058D3D3C8C2FB64028F7095317F1AF83DDDFE45CBF40953E38BCD9`.
- SHA-256 Play Debug RC2: `DEFA38594F4C3A6E0F2304B6236D4DB65488F658BB73369090EC44D0E0143D12`.
- Suite completa ejecutada una vez: 367 casos, 363 aprobados, 2 omitidos y 2 fallos existentes aislados en `MotorcyclePhotoStoreTest` en Robolectric/Windows; ambos son eliminación/limpieza de archivos con EXIF y temporales.
- Campo y Play Debug se intentaron; Campo aprobó. Play Debug quedó bloqueado por disco insuficiente después de liberar caché y antes de completar el paquete.

## Pendiente físico Samsung S22+

- GPS real al aire libre, precisión de rumbo y cambio Rumbo/Norte/Libre.
- Legibilidad 100 %/200 %, claro/oscuro y movimiento de cámara.
- Apertura/cierre de calles y clima, cache, conexión perdida y 429/timeout.
- Volumen de Ignitor, posición al mover/rotar/inclinar y consumo térmico.
- Selección Hero Ignitor 125, persistencia de foto/odómetro/mantenimientos y bloqueo durante viaje.
- Activación por Y12 Pro, música, llamadas y recuperación de audio.
