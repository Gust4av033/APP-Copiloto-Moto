# Vidrio real · tormenta medianoche, versión 2

Base: `feature/fase-2-google-maps-provider` (`b949cd6`). Versión Android `1.1.0-f2-glass2b`, código 10. Esta entrega cambia la presentación sin modificar el flujo de voz, navegación ni permisos. Los textos visibles de producto se conservan.

## Material y diagnóstico

`CopilotBackdrop` recupera el fondo azul profundo de la referencia de Ajustes: una base `#061426` con tres luces azules difusas. La esfera, el arco y la banda diagonal de la primera entrega v2 se retiraron a petición del usuario. Se conserva la paleta azul eléctrico en controles y bordes. El fondo nítido y una copia a 1/8 de resolución se precalculan una vez por tamaño y tema. `GlassPanel` toma de la copia pequeña la región correspondiente a su posición real en la ventana, incluso al desplazarse. Encima pinta tinte de contraste, reflejo, borde en gradiente y borde interior. El modo `GlassMode.LITE` elimina la copia de fondo, conservando tintes y bordes; la burbuja nativa emplea siempre material equivalente a LITE.

El rectángulo claro del contenido procedía de la capa rectangular adicional de `JourneyHero`; la elevación aplicada a superficies translúcidas en el antiguo `GlassPanel` reforzaba el contorno cuadrado. Se retiraron esas capas y la elevación. El nuevo brillo se recorta con el radio del panel. El resplandor exterior se limita al panel protagonista. El fondo es propio de APP: no lee pantallas de Waze/Maps ni difumina texto o controles.

Paleta nocturna: `#061426` de base visual, `#0A1633` en superficies, `#123A9E` en contenedores, `#2F6BFF` como acento y `#79DDFF` en luz. El botón principal oscurece el extremo superior sugerido hasta `#2864DD` para asegurar contraste blanco de 5.31:1; el extremo inferior `#1F4FD8` da 6.63:1. El botón destructivo usa coral oscuro. Los radios y dimensiones compartidos viven en `Glass.kt`; el tema usa Manrope bajo OFL (`docs/licenses/MANROPE_OFL.txt`).

El carrusel conserva sus tres pestañas y el gesto; añade flechas de 48 dp e indicador de tres puntos. Los accesos principales usan una baldosa de icono común de 44 dp. La barra inferior es vidrio LITE con borde de luz e indicador azul. Volver usa icono circular y el texto existente. La burbuja mantiene su forma y acciones, con tonos de tormenta y sin elevación sobre superficies translúcidas. El icono de modo botón ahora representa el control del casco. El icono de lanzamiento es un `VectorDrawable` original, inspirado en los rasgos de la Hero Ignitor azul proporcionada por el usuario, con círculo azul oscuro y exterior transparente. No incrusta la fotografía ni sus píxeles ni logotipos; [vista previa del vector](images/glass-v2/icon-vector-preview.png).

## Evidencia y límites

Los renders nuevos del host Compose en Android JVM se guardan en [capturas de esta revisión](images/glass-v2/after/): Inicio, Viaje y Ajustes, en claro y oscuro, al 100 % y 200 % de fuente. [Inicio](images/glass-v2/before-refinement/home-night-100.png) y [Ajustes](images/glass-v2/before-refinement/settings-night-100.png) oscuros de la primera entrega v2 permiten ver la esfera que se retiró. Las [capturas anteriores a v2](images/glass-v2/before/) permiten comparar el material original. También se incluyen [previsualización del fondo aislado](images/glass-v2/backdrop-preview.png) calculada con los mismos colores y radios del código y [previsualización del icono](images/glass-v2/icon-vector-preview.png); ambas son previsualizaciones, no capturas de la app. La [muestra de la burbuja](images/burbuja_agua_vector.png) procede del vector de producción; el panel nativo completo no se pudo ver renderizado como ventana flotante real aquí.

| Vista | Antes | Después |
| --- | --- | --- |
| Inicio oscuro | [primera entrega v2](images/glass-v2/before-refinement/home-night-100.png) | [fondo restaurado](images/glass-v2/after/home-night-100.png) |
| Ajustes oscuro | [primera entrega v2](images/glass-v2/before-refinement/settings-night-100.png) | [fondo restaurado](images/glass-v2/after/settings-night-100.png) |
| Inicio claro | [antes de v2](images/glass-v2/before/home-day.png) | [captura actual](images/glass-v2/after/home-day-100.png) |
| Ajustes claro | [antes de v2](images/glass-v2/before/settings-day.png) | [captura actual](images/glass-v2/after/settings-day-100.png) |
| Viaje con fuente grande | [antes de v2](images/glass-v2/before/trip-large-font.png) | [captura actual al 200 %](images/glass-v2/after/trip-night-200.png) |
| Burbuja vectorial | [antes](images/glass-v2/before/bubble-vector.png) | [después](images/burbuja_agua_vector.png) |

En la primera entrega v2, la suite debug completa aprobó 236 casos, omitió uno y no tuvo fallos. Para este ajuste se repitieron `SettingsContrastTest` (4), `SettingsNavigationUiTest` (15), `HomeDashboardUiTest` (6) y `VisualGlassCaptureTest` (4): **29 aprobadas, 0 fallos**. `assembleDebug` y `assembleFieldTest` terminaron correctamente. El APK nuevo Campo tiene paquete `com.gust4av033.appcopilotomoto.pruebas`, código 10, los modelos ONNX de OpenWakeWord y la misma huella de firma que el APK Campo anterior de código 9. La dependencia real OpenWakeWord 0.1.2 se resolvió desde la compilación local de su etiqueta de GitHub, ya que JitPack respondió 403 detrás del proxy; la configuración temporal de Maven local se retiró del proyecto. El icono vectorial se analizó y compiló sin errores. La suite completa no se repitió porque esta revisión solo cambia el fondo y el icono.

Pendiente en el Samsung: comprobar scroll de Inicio y Ajustes en ambos temas, fuente al 200 %, TalkBack, brillo de sol, burbuja al frente de Waze y recuperación de audio. Si el desplazamiento muestra tirones, cambiar los paneles a `GlassMode.LITE` y volver a medir antes de mantener FULL. El lector experimental de Waze y el proveedor Google Maps siguen con su prueba física separada.

## Inconsistencias de tratamiento encontradas, sin cambiar textos

- `settings_subtitle`: «Configura APP…» frente a `journey_ready_hint`: «Conectá el casco…».
- `settings_screen_on_hint`: «Puedes apagar…» frente a `overlay_hidden_hint`: «Podés mostrarla…».
- `settings_media_installation_hint`: «Activa el acceso…» frente a `navigation_confirmation`: «Activá APP…».
- `waze_screen_command`: «Mantén Waze…» frente a otras instrucciones con «mantené».
- `home_panel_hint`: «Desliza…» quedó sin uso al reemplazarlo por el indicador y las flechas.
