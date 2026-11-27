# Fase 3: UI Copilot, clima y actualización cartográfica

Nota independiente; no sustituye ni renumera la documentación oficial.

## Base y alcance

- Rama de trabajo: `feature/fase-3-copilot-mapas-optimizados`.
- Base Copilot: `afb9fb3`; referencia UI Vistazo: `d8dc941`. Ambas ramas y `main` se conservan.
- Se mantiene la composición Copilot: mapa principal, superficies Glass LITE, estado GPS/audio, dock y cámara. Se reutiliza el clima del estado compartido y su previsión existente, sin otro `WeatherController` ni peticiones desde Compose.
- Clima compacto con temperatura, condición y vigencia cuando hay datos utilizables; los estados sin datos no muestran mediciones inventadas. Al tocarlo se abren los detalles existentes.
- Lateral con 2D/3D, Capas y Centrar solo en cámara libre. Rumbo/Norte/Libre y ajuste al recorrido están en Capas. Durante el viaje se ocultan clima/configuración y se priorizan mapa, velocidad y detención mediante pulsación sostenida; accesibilidad ofrece confirmación.

## Cartografía y caché

`TripMapCatalog` consulta el [endpoint oficial `/planet/latest`](https://tiles.openfreemap.org/planet/latest), según el [README de OpenFreeMap](https://github.com/hyperknot/openfreemap#readme). Valida tamaño, esquema, fecha, dominio HTTPS, plantilla de teselas, zoom y capas necesarias antes de persistir la versión. El estilo Liberty sigue siendo local; solo se sustituye su fuente vectorial por la versión validada.

La consulta automática reutiliza el resultado reciente durante una hora. Los intentos están separados al menos un minuto. **Actualizar mapa**, dentro de Capas, revalida únicamente el pequeño TileJSON y reconstruye el estilo; no elimina la caché ni descarga regiones. Las teselas conservan su política normal de caché. Versiones anteriores, respuestas incompletas y fallos conservan la última versión validada; si no existe se muestra el atlas local. GPS y voz no dependen de esta consulta. Se mantiene el consentimiento existente, el cliente exclusivo de mapas, las restricciones de hosts, sin redirecciones, y las atribuciones OpenFreeMap/OpenMapTiles/OSM/Liberty/Natural Earth.

OpenFreeMap ofrece su instancia pública sin clave ni cobro por petición; esto no constituye una garantía contractual de disponibilidad. No se añade un proveedor de pago. El mapa no aporta por sí mismo rutas viales, tráfico, recálculo o navegación giro a giro; Waze y Google Maps siguen con su alcance existente.

## Vegetación y caso Hospital Saldaña

**Vegetación 3D** queda apagada inicialmente y guarda la elección local. Los árboles son decoración dentro de polígonos verdes cartografiados; no datos de árboles individuales. Los edificios extruidos y los polígonos originales de Liberty se conservan.

Se corrigieron causas comprobables en APP: exclusiones basadas solo en edificios visibles, truncamiento del conjunto de exclusiones y comprobación insuficiente del borde de una copa. Ahora se consulta también la geometría de edificios/agua/vías de la fuente, aunque el estilo oculte un edificio; se comprueba la copa completa. Ante errores de teselas o presupuestos excedidos se omite la decoración, en lugar de ignorar exclusiones para dibujar más árboles. Esto puede reducir la vegetación en zonas densas.

El usuario identifica un centro comercial frente al Hospital Saldaña, Planes de Renderos, San Salvador. Una [publicación de noviembre de 2024](https://www.laprensagrafica.com/elsalvador/Habitantes-de-Los-Planes-de-Renderos-rechazan-construccion-de-centro-comercial-frente-a-hospital-Saldana-20241109-0038.html) confirma el contexto del proyecto, pero no permite determinar la geometría actual de OpenStreetMap. Las consultas directas a OpenFreeMap y Overpass fallaron con `CONNECT 403` del proxy del entorno: **no se ha determinado si ese polígono concreto está desactualizado en OSM o en las teselas**. No se inventaron edificios ni se borró el bosque cartográfico. Desactivar árboles solo elimina la decoración; actualizar el proveedor tampoco garantiza una construcción ausente en OSM.

## Compatibilidad con instalaciones anteriores

Se conserva desde Vistazo el repositorio Mi Moto v3 y sus capacidades de catálogo/edición: volver a SQLite v2 de Copilot fallaría sobre una instalación que ya usa v3. Incluye Hero Ignitor 125, variante de 10 L confirmada por el usuario, manteniendo mantenimiento y valores editados. APK `1.2.1-rc4`, código 25, para permitir actualización desde rc3/código 24. No se modificaron servicios de voz, activación ni permisos.

## Validación

Verificación final de código `68399c0`: Full, 408 pruebas aprobadas y 2 omitidas; Play, 407 aprobadas y 3 omitidas; sin fallos ni errores en ambas suites. Las omisiones corresponden a condiciones existentes de variante/tipo de build. APK Campo (`assembleFullFieldTest`) y Play Debug compilados. `lintFullDebug`: 0 errores, 217 advertencias y 0 avisos informativos. No se abordó la deuda general de lint fuera del alcance. Ejecución final: `BUILD SUCCESSFUL`, 9 min 47 s.

```text
:app:testFullDebugUnitTest :app:testPlaystoreDebugUnitTest
:app:assembleFullFieldTest :app:assemblePlaystoreDebug :app:lintFullDebug
```

Se investigó `MotorcyclePhotoStoreTest` sin modificar ni el test ni el almacén: sus dos casos pasan con Robolectric SDK 35 y gráficos nativos, incluyendo orientación, privacidad EXIF, eliminación y rechazo de entradas inválidas. Los fallos anteriores no se reprodujeron; no hay evidencia suficiente para atribuirles una causa de producto o de entorno. No se ocultan ni desactivan esas pruebas.

Evidencia seleccionada: [reposo oscuro 100 %](evidencia/fase-3-copilot-optimizacion/idle-night-100-portrait.png), [reposo oscuro 200 %](evidencia/fase-3-copilot-optimizacion/idle-night-200-portrait.png), [horizontal claro 200 %](evidencia/fase-3-copilot-optimizacion/idle-day-200-landscape.png) y [viaje horizontal claro 200 %](evidencia/fase-3-copilot-optimizacion/active-day-200-landscape.png).

APK Campo: `APP-Copiloto-Moto-rc4-Campo-68399c0.apk`, paquete `com.gust4av033.appcopilotomoto.pruebas`, código 25. Firma y alineación verificados; SHA-256 `af3b84a241b6ecda21eb081a3ba2077ffb6a4e6c400af490c7f375821876058e`. Artefacto local entregado por separado, no incluido en Git.

Las capturas de Compose utilizan un renderer explícitamente rotulado **RENDERIZADOR DE PRUEBA; sin cartografía real**. Sirven para contraste, tamaños, navegación y solapamientos; no prueban MapLibre/OpenGL, precisión GPS ni casco. El emulador nativo no arrancó por espacio insuficiente para userdata.

Entorno: JDK 17, SDK local y Robolectric SDK 35. JitPack no estaba disponible: se utilizó un repositorio Maven temporal con openwakeword-android 0.1.2 reconstruido desde el tag oficial (`b49f3ab14cf558ac0c2aef73a5e6c83da7a08558`), sin editar código ni modelos. Se adaptó su herramienta de compilación al entorno y ONNX 1.23.2, ya forzado por APP. No se modificaron dependencias del proyecto ni se verificó el binario distribuido por JitPack; esta limitación también aplica al APK Campo generado aquí.

Pendiente físico en S22+ / Y12 Pro: instalación sobre rc3 sin pérdida de Mi Moto; mapas en línea, actualización/fallo y cámara libre/rumbo/norte; comparar el centro comercial con árboles apagados; rendimiento y edificios reales; GPS/recorrido; activación, música, llamadas y recuperación del audio. No se declaran comprobados sin esa prueba.
