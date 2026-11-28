# Fase 3 — UI Pro
Nota independiente; ejecución cerrada el 28-11-2026. No sustituye documentación oficial.
Base: `9769c31`, `origin/feature/fase-3-copilot-mapas-optimizados`.
Rama: `feature/fase-3-ui-pro`; código: `9e6b04f`. Se conservan las demás ramas.

## Cambios
- Cápsula de 48 dp con estado y clima compartidos; permanece en viaje, sin otra petición/controlador.
- Temperatura tabular/negrita; dato anterior atenuado con edad; sin «Vigente» ni valores inventados.
- Insignia accesible de GPS/audio y lluvia alta si existe pronóstico para la próxima hora.
- Detalles solo en reposo/parada confirmada; velocidad ausente o caducada no desbloquea la UI activa.
- `rideUiPhase` y su prueba copiados de Vistazo, sin modificar GPS ni servicios.
- Controles circulares de 48 dp: Capas, perspectiva por geometría 2D/3D, navegador existente y Centrar contextual.
- En marcha: clima, navegador disponible, Centrar contextual, velocidad y Detener; al parar vuelven destinos/2D-3D, no Capas.
- Cámara con márgenes para cápsula, controles, atribución y dock; requiere comprobar la proyección nativa en teléfono.
- Glass LITE, degradados de borde, cifras legibles, ayudas acotadas y grupos separados; sin blur/animación continua.
- Háptico únicamente al completar Detener; pulsación breve/cancelada no detiene ni vibra.
- APK rc5, código 26. Intactos audio, activación, GPS, SQLite v3, permisos, proveedores y documentación protegida.
- Etapa 4 opcional (fecha, opacidad de áreas verdes y enlaces OSM por pulsación larga) queda pendiente.

## Comprobaciones ejecutadas
- Dirigida 1: `testFullDebugUnitTest --tests '*MapWeatherUiTest' --tests '*RideUiPhaseTest'`: OK tras corregir el nombre del timestamp.
- Dirigida 2/3: `testFullDebugUnitTest` filtrada a TripMapUiTest, MapWeatherUiTest, HomeDashboardUiTest, SettingsContrastTest y RideUiPhaseTest: 2 fallos de pruebas corregidos (fixture antiguo/coherencia temporal y árbol semántico fusionado).
- Repetición focalizada TripMapUiTest + MapWeatherUiTest: OK; incluye tamaño táctil, edad, márgenes y háptico.
- Regresión completa, una vez: `testFullDebugUnitTest` (404: 401 OK, 2 omitidas, 1 fallo) y `testPlaystoreDebugUnitTest` (404: 400 OK, 3 omitidas, 1 fallo). Ambos fallos: aviso de licencias todavía etiquetado rc4.
- `SettingsContrastTest` incluido en esas suites: sin fallos (5 pruebas en Full). No se ejecutó lint en este avance.
- Excepción mínima al listado de archivos: primera línea de `assets/licenses/NOTICE.txt` sincronizada a rc5, sin cambiar las licencias.
- Revalidación `SettingsNavigationUiTest.bundledLicensesOpenOfflineAndClose` en Full y Play: 1/1 OK por variante; no se repitieron las suites completas.
- `assembleFullFieldTest` y `assemblePlaystoreDebug`: OK; reempaquetados con el aviso corregido. Firma/alineación Campo verificadas.
- Entorno: adaptación Maven temporal de openWakeWord ya documentada; código/modelos originales, no se commitea. No equivale a verificar el binario JitPack.
- No se consultaron OSM, Overpass ni OpenFreeMap desde el entorno. No se afirma probado el mapa nativo/GPS/casco.

APK Campo: `APP-Copiloto-Moto-rc5-Campo-9e6b04f.apk`; SHA-256 `68b21e330102534b6fcd5584077f03cd8953edfb9e7d2dfe69c913d3955ce2ec`.

## Cuatro capturas con renderer de prueba, sin cartografía real
[Reposo noche 100 %](evidencia/fase-3-ui-pro/idle-night-100-portrait.png) · [Reposo noche 200 %](evidencia/fase-3-ui-pro/idle-night-200-portrait.png)
[Viaje vertical noche 100 %](evidencia/fase-3-ui-pro/active-night-100-portrait.png) · [Viaje horizontal día 200 %](evidencia/fase-3-ui-pro/active-day-200-landscape.png)

Pendiente S22+ / Y12 Pro: clima/botones a pleno sol, guantes, háptico, cápsula sin tapar la moto, 2D/3D y seguimiento/cámara libre.
Comprobar instalación sobre rc4 sin pérdida de Mi Moto; activación, música, llamadas y recuperación Bluetooth sin regresiones.
