# Mapa 3D, clima y catálogo de motos

Avance adelantado de Fase 3, sobre `feature/fase-3-mapa-principal` (`e010717`).
No redefine ni renumera las fases oficiales. Candidato no comercial `1.2.1-rc2`, código 23.

Retomado el 26 de noviembre de 2026 sobre la misma rama y conservando los cambios locales. Las ramas posteriores de Copilot y restauración rc2 se consideran borradores: no se incorporan nuevos cambios ni se cambia la base. El sufijo de versión rc2 de este APK no indica que proceda de la rama de restauración.

## Comportamiento implementado

- El mapa ocupa la pantalla inicial. La referencia conceptual del usuario guía la composición: marca y estado de GPS/sesión/audio arriba, clima flotante a la izquierda, botones de cámara en el borde derecho y un resumen con Mi Moto / Iniciar o Detener / Ajustes abajo. La marca abre Inicio y el resumen abre Viaje. Se elimina la barra genérica de pestañas solo en el mapa. Los paneles permiten desplazarse con letra grande y el inferior pasa a una columna lateral en horizontal.
- MapLibre Native OpenGL 13.7.1 carga calles vectoriales de OpenFreeMap, con edificios extruidos desde zoom 14 donde los datos los incluyen. Cámara inclinada de 52°, vista 2D y modos Rumbo, Norte y Libre. El rumbo exige GPS vigente, velocidad de al menos 2 m/s y precisión angular de hasta 30°; sin evidencia suficiente se usa norte como referencia.
- Las zonas verdes cartografiadas pueden mostrar copas geométricas en tres niveles sobre troncos desde zoom 15. Son ilustraciones, no posiciones reales de árboles individuales. La generación respeta huecos del polígono y excluye agua, edificios y calles detectados; se limita a 64 árboles visibles, se calcula fuera del hilo principal y se suspende al ocultar el mapa o pasar a 2D. Sin cobertura verde no se inventa un bosque.
- La moto es un avatar original con volumen: ruedas, tanque, asiento, faro, manillar y espejos mediante geometría tridimensional nativa. Está anclado a las coordenadas y comparte la cámara del mapa. Su tamaño se exagera para que se lea; no representa dimensiones reales ni es un modelo de fábrica. En vista 2D o zoom lejano se utiliza la ilustración plana. El catálogo comienza con la Hero Ignitor 125 azul; colores, otros modelos y especificaciones técnicas quedan pendientes.
- Clima utiliza `WeatherProvider` con Open-Meteo: temperatura, condición y viento estimados, más las próximas doce horas disponibles con probabilidad de precipitación. Permite consultar ubicación GPS reciente del viaje o destino propio con coordenadas ya guardadas. No inventa clima si falta señal, ubicación o respuesta. Distingue consulta anterior y datos vencidos, e incluye atribución y hora/zona horaria del proveedor.
- Mi Moto elige un modelo del catálogo incluido y guarda su ID estable. La migración SQLite 1 → 2 conserva nombre, modelo escrito, kilometraje manual, foto anterior y mantenimientos. Ya no ofrece importar fotos nuevas; permite eliminar la anterior. El kilometraje manual continúa separado de la distancia GPS.

## Qué se aprovechó de Copilot

Del commit `c0b10adcd1d2a2c7d5d7d194765af316fba79dd6` se adaptaron el filtro de rumbo, los tres modos de cámara, la política de recursos vectoriales/consentimiento v2 y el estilo Liberty. Se mantuvieron el inicio con atlas local, el avatar de moto y el diseño de esta rama. La paleta nocturna ahora cubre también etiquetas, edificios, usos del suelo y jerarquías de calles.

No se trasladó el cambio de verificación/reintentos Bluetooth: `OpenWakeWordProvider.kt` y `TripVoiceService.kt` conservan la base probada. Clima no habla, no pide foco ni abre otra captura GPS. Los controles de música, llamadas, activación y recuperación de audio no se modifican.

La referencia `archive/copilot-mapa-2026-11-25` conserva `c0b10ad`. Lo descrito arriba ya estaba adaptado en los cambios locales antes de esta reanudación. Siguiendo la última indicación de ignorar los borradores, esta entrega no modifica ni elimina esas ramas.

## Actualización de interfaz ligera · 26-11-2026

La continuación se integró en `feature/fase-3-mapa-principal-copilot` sobre la
base permitida `origin/feature/fase-3-mapa-principal` (`1199e3e`), mediante el
merge `4a0c6ce`; no se usó `feature/fase-3-recuperacion-rc2` ni el archivo de
Copilot como base. Etapa A (`5893248`) simplifica el mapa y Etapa B (`c1fded0`)
reduce densidad de rótulos. La pantalla Viaje deja de insertar `TripMapCard`:
presenta estado de sesión, resumen rotulado cuando existe una sesión terminada
y acceso al mapa principal.

El estilo Liberty ahora difiere POI por rangos de rank hasta zoom 17, 18 y 19
(transporte desde 17), y aeropuertos desde 12. Las etiquetas secundarias de
lugar comienzan entre zoom 4 y 10 según jerarquía. Se verificó estructuralmente
que las otras 100 capas, incluidos nombres de calles y escudos, conservan sus
propiedades. La interfaz se comprueba en `TripMapUiTest` y `MapWeatherUiTest`;
las capturas nuevas están identificadas como fixtures Compose sin cartografía
real. No hay `adb` disponible para validar visualmente el estilo con teselas.

## Proveedores, privacidad y costes

- [OpenFreeMap](https://openfreemap.org/) ofrece la instancia pública sin clave y sin tarifa por solicitudes, también para aplicaciones comerciales. Sus [condiciones](https://openfreemap.org/tos/) no garantizan exactitud ni continuidad. Esta entrega no descarga regiones offline. Mantiene prefetched tiles apagadas, FPS máximo 30, pausa al ocultarse y caché HTTP privada de 64 MiB, además de la caché interna de MapLibre. El atlas Natural Earth incluido permite abrir el mapa sin conexión.
- El consentimiento anterior de raster OSM no habilita el nuevo proveedor. La divulgación v2 explica recursos enviados a `tiles.openfreemap.org`, IP y zona inferible. Solo se permiten recursos HTTPS del proveedor; retirar autorización o dejar el mapa cancela sus solicitudes. Las atribuciones enlazan OpenFreeMap, OpenMapTiles, OpenStreetMap y Liberty. Se incluyen licencias originales y crédito a las modificaciones.
- [Open-Meteo](https://open-meteo.com/en/terms) ofrece la API gratuita para uso **no comercial**, con límites de 10 000 consultas/día, 5 000/hora y 600/minuto. Los datos son CC BY 4.0. Un producto comercial debe contratar el servicio adecuado o alojarlo; no se añadió una clave ni un servicio facturable.
- Clima tiene autorización separada y transmite únicamente coordenadas redondeadas a dos decimales del área seleccionada, además de la IP. No transmite nombre de destino, recorrido, historial, moto ni audio. Caché solo en memoria de hasta ocho zonas, renovación por zona cada 15 minutos, caducidad a los 30 y separación mínima de un minuto entre intentos de la misma zona. Cambiar de zona puede iniciar otra consulta. Las solicitudes se cancelan al ocultar la app/mapa o desactivar Clima; una respuesta tardía no restaura datos revocados.
- Se actualizaron las adendas de `privacy-policy.html` y los avisos de la app para describir estos proveedores. Los documentos oficiales de fases/UI se mantienen. No se agregan permisos Android. Play continúa sin Accesibilidad y con las restricciones preexistentes.

## Validación

Suite JVM completa: **384 pruebas, 382 aprobadas, 0 fallos, 0 errores y 2 omitidas**. Incluye la migración de Mi Moto, persistencia, parser/caché/cancelación de clima, política de cámara y red, exclusiones y límite de vegetación, controles Compose y contraste. Las 24 pruebas enfocadas también pasan. Compilaciones **Full Campo, Play Debug y Full Campo AndroidTest completadas**. APK Campo `1.2.1-rc2-campo` (código 23), paquete separado `com.gust4av033.appcopilotomoto.pruebas`; firma y alineación ZIP de 16 KiB verificadas. Las dos omisiones corresponden a pruebas específicas de Play y release que no aplican al sabor Full Debug. No hay cambios en los manifiestos ni en `TripVoiceService.kt` / `OpenWakeWordProvider.kt`. La prueba del diálogo de clima configura la escala real del sistema al 200 % y comprueba que todo el aviso es desplazable antes de aceptar.

La red restringida del entorno no autoriza `tiles.openfreemap.org` ni `api.open-meteo.com`: las consultas HTTPS de prueba reciben 403 del proxy. Las páginas de condiciones sí se consultaron mediante el buscador. Las pruebas de transporte con respuestas identificadas verifican parser, permisos, caducidad y cancelación; no constituyen evidencia de clima o calles reales en el teléfono.

La compilación usa JDK 17, SDK 36 y la reconstrucción temporal desde el código oficial de openWakeWord Android 0.1.2 ya usada en la entrega anterior, debido al acceso bloqueado a JitPack. No hay stubs de audio ni modificaciones a los modelos, pero no se afirma identidad binaria con el artefacto publicado en JitPack.

El renderizado nativo de la moto anterior a este último ajuste de interfaz ya había pasado. **No se completó una nueva ejecución nativa de la composición final y los árboles**: tras reiniciar el entorno, el emulador rechazó el arranque por espacio insuficiente (4 642 MB disponibles frente a 7 372 MB requeridos). Se conserva la prueba instrumentada compilada para repetirla; no se confunde con las pruebas JVM aprobadas. Los archivos `moto-3d-prueba-*.png` son evidencia de la ejecución anterior, no capturas de la interfaz final.

## Evidencia visual

Las capturas de interfaz provienen del código Compose ejecutado en Android JVM. El área del mapa y el clima están identificados como escenarios sintéticos: sirven para revisar composición y accesibilidad, no para acreditar datos de proveedores.

| Pantalla | Oscuro | Claro |
| --- | --- | --- |
| Mapa principal, 100 % | [Ver](images/fase-3-mapa-3d-clima/map-night-100.png) | [Ver](images/fase-3-mapa-3d-clima/map-day-100.png) |
| Mapa principal, 200 % | [Ver](images/fase-3-mapa-3d-clima/map-night-200.png) | [Ver](images/fase-3-mapa-3d-clima/map-day-200.png) |
| Mi Moto, 100 % | [Ver](images/fase-3-mapa-3d-clima/mi-moto-night-100-profile.png) | [Ver](images/fase-3-mapa-3d-clima/mi-moto-day-100-profile.png) |
| Mi Moto, 200 % | [Ver](images/fase-3-mapa-3d-clima/mi-moto-night-200-profile.png) | [Ver](images/fase-3-mapa-3d-clima/mi-moto-day-200-profile.png) |

## Validación física y próximos pasos

En S22+ con Y12 Pro: autorizar por separado mapa y clima; comprobar calles, textos, edificios donde existan y clima actual/por horas; mover/rotar/recentrar y alternar 2D/3D; apagar datos y revocar cada servicio; comprobar modo claro/oscuro y fuente al 200 %. Evaluar temperatura, batería y fluidez con mapa 3D y sesión prolongada.

Repetir activación, órdenes consecutivas, música, llamada entrante/saliente, reconexión del casco y recuperación de audio con Waze al frente y al volver al mapa. Estas pruebas físicas no se sustituyen por las del emulador.

Se mantienen Waze/Maps como navegador externo. Búsqueda propia de lugares, rutas viales, instrucciones giro a giro, tráfico, recálculo, avisos hablados de clima y personalización avanzada no forman parte de esta entrega. Mis viajes y Preparar viaje completos continúan como prioridades posteriores.
