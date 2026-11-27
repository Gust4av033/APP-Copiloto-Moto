# UI de vistazo — avance separado

Base: `origin/feature/fase-3-mapa-principal` (`284bcb7`); no existía `feature/fase-3-ui-mapa-ligero`.
Trabajo exclusivo en `feature/fase-3-ui-vistazo`; sin incorporar borradores ni modificar documentación oficial.

- Fases puras IDLE / RIDING / STOPPED_ON_ROUTE: >8 km/h por 3 s, <3 km/h por 5 s, con muestras válidas distintas. Datos vencidos no desbloquean la UI en marcha. Estado conservado sobre la navegación y recreación.
- En marcha: velocidad protagonista, parada protegida, atribución, advertencias GPS/audio y escucha estática. Cámara reducida a Centrar en modo Libre. Sin Mi Moto, Ajustes, clima ni configuración superpuestos.
- Números tabulares en negrita; velocidad sin precisión/vencida = «—». Sin métricas ni trazo del viaje anterior en el mapa inactivo; conserva el último punto conocido como antiguo, sin devolver la cámara al mundo.
- `RouteHudState?`, `ManeuverBanner` y `EtaBar` preparados; producción null. Fuente visible cuando exista ruta; instrucciones/datos vencidos ocultos. Fixtures solo de pruebas.
- Trazo fino discontinuo gris azulado. Ruta reservada cian sólida con borde oscuro y debajo de la moto; fuente vacía, `showRoute(points)` / `clearRoute()`. Sin cambios de proveedor, GPS ni permisos.
- Paneles y parada en marcha con fondo oscuro 96 %; cifras ≥7:1 aun sobre blanco. Escuchando usa voz existente, borde, icono y texto sin pulso.
- Parada: alternativa autorizada de mantener 600 ms con anillo finito; soltar antes o salir cancela. Accesibilidad/teclado abren confirmación. No hay stop aplazado: garantizar su entrega tras destruir la UI exigiría un propietario persistente fuera de esta capa. No se modificó TripVoiceService ni audio, activación o botón Y12 Pro.
- Búsqueda siguiente: `onChooseMapDestination` nullable; el acceso solo aparece cuando haya selector real, fuera de RIDING. Sin motor, buscador ficticio ni ruta supuesta. Waze continúa independiente.

## Validación
- Etapa 1: `testFullDebugUnitTest --tests '*RideUiPhaseTest'`: aprobado.
- Etapa 2: pruebas de UI movingMap / idleMap / routeHud: aprobadas.
- Etapas 3–5: intento intermedio falló por token de contraste aún no compilado; siguiente ejecución detectó un fixture de accesibilidad que inyectaba toque en vez de acción semántica. Corregido el fixture; pruebas focalizadas de etapas 3–5 aprobadas (incluye contraste, escucha, parada y regreso a parada para elegir destino).
- Una ejecución completa: `testFullDebugUnitTest` 398 casos / 396 aprobados / 2 omitidos; `testPlaystoreDebugUnitTest` 398 / 395 / 3. Sin fallos ni errores. `SettingsContrastTest` incluido y aprobado.
- `assembleFullFieldTest`, `lintFullDebug` y `assembleFullFieldTestAndroidTest`: aprobados; comando final terminó BUILD SUCCESSFUL en 9 min 16 s.
- Adaptación temporal de openWakeWord/JitPack ya documentada en el entorno, sin cambios de dependencia commiteados; no verifica el binario remoto de JitPack.
- Revisión final: cuatro capturas Compose con renderer de prueba explícito, sin cartografía nativa. Ajuste del estado de audio al 200 %: pruebas IDLE de ambos sabores, build Campo y lint aprobados. Conservación del último punto y revocación GPS: 4 pruebas focalizadas Full aprobadas; compilación Kotlin Play, build Campo y lint aprobados (6 min 36 s). No se repitió la suite completa.
- Render local MapLibre: prueba ampliada para ruta cian separada; ejecución bloqueada, AVD exige 7372,8 MB para userdata y solo hay ~4634 MB disponibles. No equivale a render verificado.

Capturas: [IDLE día 100 %](images/fase-3-ui-vistazo/idle-day-100.png), [IDLE noche 200 %](images/fase-3-ui-vistazo/idle-night-200.png), [RIDING día 100 %](images/fase-3-ui-vistazo/riding-day-100.png), [RIDING noche 200 %](images/fase-3-ui-vistazo/riding-night-200.png).

Pendiente S22+ / Y12 Pro: lectura de un segundo a pleno sol montado en la moto, toques con guantes, flechas/ruta/trazo nativos y confirmar activación, música, llamadas y recuperación Bluetooth sin regresión.

## Revisión visual del reposo — 27/11
Cabecera reducida a marca y opciones separadas; GPS/audio en etiquetas compactas y clima flotante más pequeño. Panel inferior unificado con Iniciar viaje ancho y accesos Mi Moto / Viaje / Ajustes. La etiqueta de sesión redundante se elimina; las acciones reales siguen indicando iniciar o detener. Sin cambios de cartografía, fases, parada protegida, audio, GPS ni permisos.
Comparación revisada: [día 100 %](images/fase-3-ui-vistazo/reposo-renovado/idle-day-100.png) y [noche 200 %](images/fase-3-ui-vistazo/reposo-renovado/idle-night-200.png), con renderer de pruebas identificado.
28 pruebas de TripMapUiTest, MapWeatherUiTest y SettingsContrastTest aprobadas. Build Campo, compilación Kotlin Play y lint aprobados; no se repite la suite completa ya ejecutada en el cierre anterior.
