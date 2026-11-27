# Fase 3 · interfaz ligera del mapa

## Rama y base

- Rama de entrega: `feature/fase-3-mapa-principal-copilot`, continuando la rama
  Copilot existente como se acordó.
- Base de mapa: `origin/feature/fase-3-mapa-principal`, último commit disponible
  al integrar: `1199e3e`.
- Integración no lineal de la base: `4a0c6ce`. No se usaron
  `feature/fase-3-recuperacion-rc2` ni `archive/copilot-mapa-2026-11-25` como
  base.
- Etapa A: `5893248`. Etapa B: `c1fded0`. Etapa C: `7017e22`.

## Cambios realizados

### A · mapa principal

- Se eliminó el encabezado de marca y se dejó la Ignitor como avatar en una
  pastilla de estado. GPS y audio conservan icono y texto breve; la temperatura
  solo se pinta cuando el dato es usable.
- GPS, sesión, audio, clima, mapa por Internet y navegador elegido permanecen
  accesibles desde el detalle. El detalle conserva la activación explícita de
  ubicación/clima y la atribución Open-Meteo.
- Un control alterna Norte/Rumbo; 2D/3D tiene iconos distintos. Libre sigue
  entrando por gesto; Centrar y Ver trazo aparecen solo en ese modo.
- En reposo el dock único contiene Mi Moto, Iniciar viaje y Ajustes. En sesión
  contiene velocidad, con acceso visible a detalles, y Detener. Con fuente al
  200 % se apilan para evitar recortes, también en horizontal.
- La atribución del mapa queda visible sobre el dock. Se preservaron
  `ride_map_viewport`, los modos LITE y las APIs `NativeTripMapController` /
  `TripMapCameraMode`.

### B · estilo local

Se eligió mantener las categorías POI existentes y diferirlas hasta zoom
cercano: `poi_r1` desde 17, `poi_r7` desde 18, `poi_r20` y transporte desde 19
y 17 respectivamente; aeropuertos desde 12. Las etiquetas secundarias de
lugares pasan a zoom 4–10 según jerarquía. Se mantuvieron nombres de calles,
escudos y capas de carretera.

El JSON se pudo parsear y se compararon sus 111 capas antes/después: solo
cambiaron los `minzoom` de las 11 capas objetivo; las otras 100 conservaron sus
propiedades. No se cambiaron fuentes, red, proveedores ni datos.

### C · pantalla Viaje

Se retiró el mapa duplicado (`TripMapCard`). Viaje conserva los controles de
sesión, muestra «Último viaje» únicamente para una sesión finalizada y ofrece
«Ver en el mapa». Si no hay datos finalizados, lo expresa sin inventar métricas.
El resumen sigue siendo temporal y reside en el estado local de sesión.

Las pruebas de mapa verifican que GPS, atribución, consentimiento del mapa por
Internet, navegador y estados continúan en la pantalla Mapa. Se actualizaron
también los tests antiguos del dock para comprobar la composición principal
actual en `MAP`, y la navegación a Mi Moto desde ese dock.

## Conteo y geometría

El diagnóstico inicial registró aproximadamente siete bloques en reposo,
seis controles permanentes de cámara/acción y métricas antiguas visibles sin
sesión; Viaje además embebía un segundo mapa.

| Estado del mapa | Antes | Después |
| --- | --- | --- |
| Reposo | ~7 bloques y 6 controles permanentes | 5 elementos: estado, cámara, 2D/3D, atribución y dock |
| Sesión activa | Métricas y controles redundantes | 3 elementos: velocidad, Detener y atribución |
| Viaje | Segundo mapa con controles | Sin mapa embebido; controles de sesión, resumen y acceso al mapa |

Las pruebas de composición confirman en vertical que el mapa libre supera el
70 % a fuente 100 % y el 50 % a fuente 200 %. En horizontal, el dock queda en
columna lateral y se comprobó que no invade el viewport. Los estados de cámara
y perspectiva son controles distintos y tienen descripciones accesibles.

## Capturas

Las imágenes anteriores ya guardadas en la documentación son la referencia
previa al refactor en vertical: [mapa noche 100 %](./images/fase-3-mapa-3d-clima/map-night-100.png),
[mapa día 100 %](./images/fase-3-mapa-3d-clima/map-day-100.png),
[mapa noche 200 %](./images/fase-3-mapa-3d-clima/map-night-200.png) y
[Viaje noche 200 %](./images/fase-3-viaje-mapa/trip-night-200.png). No había
capturas previas en horizontal.

Las capturas posteriores cubren reposo/sesión, claro/oscuro, fuente 100/200 %
y vertical/horizontal:

| Estado | Escala | Orientación | Día | Noche |
| --- | --- | --- | --- | --- |
| Reposo | 100 % | Vertical | [PNG](./images/fase-3-ui-mapa-ligero/idle-day-100-portrait.png) | [PNG](./images/fase-3-ui-mapa-ligero/idle-night-100-portrait.png) |
| Reposo | 200 % | Vertical | [PNG](./images/fase-3-ui-mapa-ligero/idle-day-200-portrait.png) | [PNG](./images/fase-3-ui-mapa-ligero/idle-night-200-portrait.png) |
| Reposo | 100 % | Horizontal | [PNG](./images/fase-3-ui-mapa-ligero/idle-day-100-landscape.png) | [PNG](./images/fase-3-ui-mapa-ligero/idle-night-100-landscape.png) |
| Reposo | 200 % | Horizontal | [PNG](./images/fase-3-ui-mapa-ligero/idle-day-200-landscape.png) | [PNG](./images/fase-3-ui-mapa-ligero/idle-night-200-landscape.png) |
| Sesión | 100 % | Vertical | [PNG](./images/fase-3-ui-mapa-ligero/active-day-100-portrait.png) | [PNG](./images/fase-3-ui-mapa-ligero/active-night-100-portrait.png) |
| Sesión | 200 % | Vertical | [PNG](./images/fase-3-ui-mapa-ligero/active-day-200-portrait.png) | [PNG](./images/fase-3-ui-mapa-ligero/active-night-200-portrait.png) |
| Sesión | 100 % | Horizontal | [PNG](./images/fase-3-ui-mapa-ligero/active-day-100-landscape.png) | [PNG](./images/fase-3-ui-mapa-ligero/active-night-100-landscape.png) |
| Sesión | 200 % | Horizontal | [PNG](./images/fase-3-ui-mapa-ligero/active-day-200-landscape.png) | [PNG](./images/fase-3-ui-mapa-ligero/active-night-200-landscape.png) |

Las imágenes posteriores son capturas Compose ejecutadas con un renderer
marcado «Mapa sustituido; sin cartografía real». Sirven para evaluar
composición, texto y solapes; no muestran el estilo Liberty ni acreditan la
densidad de POI. `adb` no está disponible en este entorno, así que falta la
captura nativa del mapa con teselas para justificar visualmente el cambio de
estilo.

## Validación ejecutada

| Comando | Resultado real |
| --- | --- |
| `testFullDebugUnitTest --tests '*TripMapUiTest' --tests '*MapWeatherUiTest'` | Correcto: 19 + 12 pruebas, sin fallos |
| `testFullDebugUnitTest --tests '*HomeDashboardUiTest' --tests '*MotorcycleUiTest' --tests '*TripMapUiTest' --tests '*MapWeatherUiTest'` | Correcto; regresiones de dock/mapa y accesos de Mi Moto |
| `testFullDebugUnitTest --tests '*SettingsContrastTest'` | Correcto: 4 pruebas |
| `testFullDebugUnitTest` | Falla: 398 pruebas, 2 fallos y 2 omitidas. Los 2 fallos están en `MotorcyclePhotoStoreTest` (líneas 41 y 54), fuera de los archivos de esta entrega |
| `testPlaystoreDebugUnitTest` | Falla: 398 pruebas, los mismos 2 fallos de `MotorcyclePhotoStoreTest` y 3 omitidas |
| `assembleFullFieldTest assemblePlaystoreDebug` | Correcto: `BUILD SUCCESSFUL` |
| `git diff --check` | Correcto, sin errores de whitespace |
| Parseo/contraste de `liberty.json` | Correcto: 111 capas; solo 11 umbrales de zoom cambiaron |

No se ejecutó una prueba de Android nativa del mapa ni una prueba en hardware.
No se reporta ninguna como completada.

## Deuda, decisiones y pruebas físicas pendientes

- La etapa D opcional de navegación unificada no se realizó. Se conservan
  `CopilotScreen`, `visibleFor`, `BackHandler` y los destinos de comandos,
  notificación y burbuja; dejar la barra secundaria como está evita ampliar el
  riesgo de navegación fuera de este alcance.
- Pendiente revisar capturas nativas con teselas en claro/oscuro y 100/200 %;
  hasta entonces la modificación de densidad de rótulos está estructuralmente
  verificada, no visualmente certificada.
- En S22+ con Y12 Pro siguen pendientes por separado: legibilidad al sol,
  toques con guantes, toques accidentales en marcha y alcance con una mano.
- También queda confirmar físicamente que activación por frase y botón Y12 Pro,
  música, llamadas y recuperación Bluetooth no cambiaron. Esta entrega no editó
  los servicios de voz/audio, el flujo de activación, GPS ni proveedores de
  mapas; no sustituye esas pruebas.
