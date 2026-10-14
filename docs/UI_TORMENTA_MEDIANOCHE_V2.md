# Vidrio real · tormenta medianoche, versión 2

Base: `feature/fase-2-google-maps-provider` (`b949cd6`). Versión Android `1.1.0-f2-glass2`, código 9. Esta entrega cambia la presentación sin modificar el flujo de voz, navegación ni permisos. Los textos visibles de producto se conservan.

## Material y diagnóstico

`CopilotBackdrop` dibuja una esfera eléctrica con borde reconocible, un arco celeste, una banda diagonal y un brillo índigo acotado. Precalcula el fondo nítido y una copia a 1/8 de resolución una vez por tamaño y tema. `GlassPanel` toma de la copia pequeña la región correspondiente a su posición real en la ventana, incluso al desplazarse. Encima pinta tinte de contraste, reflejo, borde en gradiente y borde interior. El modo `GlassMode.LITE` elimina la copia de fondo, conservando tintes y bordes; la burbuja nativa emplea siempre material equivalente a LITE.

El rectángulo claro del contenido procedía de la capa rectangular adicional de `JourneyHero`; la elevación aplicada a superficies translúcidas en el antiguo `GlassPanel` reforzaba el contorno cuadrado. Se retiraron esas capas y la elevación. El nuevo brillo se recorta con el radio del panel. El resplandor exterior se limita al panel protagonista. El fondo es propio de APP: no lee pantallas de Waze/Maps ni difumina texto o controles.

Paleta nocturna: `#040A1C` de base, `#0A1633` en superficies, `#123A9E` en contenedores, `#2F6BFF` como acento y `#79DDFF` en luz. El botón principal oscurece el extremo superior sugerido hasta `#2864DD` para asegurar contraste blanco de 5.31:1; el extremo inferior `#1F4FD8` da 6.63:1. El botón destructivo usa coral oscuro. Los radios y dimensiones compartidos viven en `Glass.kt`; el tema usa Manrope bajo OFL (`docs/licenses/MANROPE_OFL.txt`).

El carrusel conserva sus tres pestañas y el gesto; añade flechas de 48 dp e indicador de tres puntos. Los accesos principales usan una baldosa de icono común de 44 dp. La barra inferior es vidrio LITE con borde de luz e indicador azul. Volver usa icono circular y el texto existente. La burbuja mantiene su forma y acciones, con tonos de tormenta y sin elevación sobre superficies translúcidas. El icono de modo botón ahora representa el control del casco. El icono de lanzamiento usa una ilustración inspirada en la Hero Ignitor azul proporcionada por el usuario, dentro de un círculo con exterior transparente.

## Evidencia y límites

Los renders del host Compose en Android JVM se guardan en [capturas posteriores](images/glass-v2/after/): Inicio, Viaje y Ajustes, en claro y oscuro, al 100 % y 200 % de fuente. Las [capturas disponibles de la versión anterior](images/glass-v2/before/) permiten comparar Inicio, Viaje y Ajustes. La [muestra posterior de la burbuja](images/burbuja_agua_vector.png) procede del vector de producción y está marcada como previsualización; el panel nativo completo no se pudo ver renderizado como ventana flotante real aquí. No hay captura previa equivalente para todas las combinaciones de Viaje/Ajustes con fuente al 200 %.

| Vista | Antes | Después |
| --- | --- | --- |
| Inicio oscuro | [captura](images/glass-v2/before/home-night.png) | [captura](images/glass-v2/after/home-night-100.png) |
| Inicio claro | [captura](images/glass-v2/before/home-day.png) | [captura](images/glass-v2/after/home-day-100.png) |
| Ajustes claro | [captura](images/glass-v2/before/settings-day.png) | [captura](images/glass-v2/after/settings-day-100.png) |
| Viaje con fuente grande | [captura](images/glass-v2/before/trip-large-font.png) | [captura al 200 %](images/glass-v2/after/trip-night-200.png) |
| Burbuja vectorial | [antes](images/glass-v2/before/bubble-vector.png) | [después](images/burbuja_agua_vector.png) |

`SettingsContrastTest`, `SettingsNavigationUiTest`, `HomeDashboardUiTest` y las capturas nuevas comprueban colores, navegación y render. La suite debug completa aprobó 236 casos, omitió uno y no tuvo fallos; después del último pulido, los cuatro casos de capturas volvieron a pasar. `assembleDebug` y `assembleFieldTest` terminaron correctamente. La compilación usa la dependencia real OpenWakeWord 0.1.2 construida localmente desde su etiqueta de GitHub, con modelos ONNX y dependencia transitiva; el repositorio JitPack respondió 403 detrás del proxy. La configuración temporal de Maven local se retiró del proyecto tras producir los APK. El código del producto conserva la dependencia normal de JitPack. El APK `fieldTest`, firmado con clave debug, instala el paquete `com.gust4av033.appcopilotomoto.pruebas` al lado de APP y contiene los modelos de voz reales.

Pendiente en el Samsung: comprobar scroll de Inicio y Ajustes en ambos temas, fuente al 200 %, TalkBack, brillo de sol, burbuja al frente de Waze y recuperación de audio. Si el desplazamiento muestra tirones, cambiar los paneles a `GlassMode.LITE` y volver a medir antes de mantener FULL. El lector experimental de Waze y el proveedor Google Maps siguen con su prueba física separada.

## Inconsistencias de tratamiento encontradas, sin cambiar textos

- `settings_subtitle`: «Configura APP…» frente a `journey_ready_hint`: «Conectá el casco…».
- `settings_screen_on_hint`: «Puedes apagar…» frente a `overlay_hidden_hint`: «Podés mostrarla…».
- `settings_media_installation_hint`: «Activa el acceso…» frente a `navigation_confirmation`: «Activá APP…».
- `waze_screen_command`: «Mantén Waze…» frente a otras instrucciones con «mantené».
- `home_panel_hint`: «Desliza…» quedó sin uso al reemplazarlo por el indicador y las flechas.
