# Tormenta refinada · vidrio y formas, versión 3

Base: `feature/fase-2-integrada`, `f65378dbccaade8762061616a6c44674f80f59a7`.
Trabajo: `feature/ui-storm-refined`. Continúa el checkpoint `d198476`.
Versión Android: `1.1.0-f2-glass3`, código 12.

## Cambios

Se mantiene la dirección azul tormenta. El fondo conserva luces difusas propias,
con azul más profundo y caída de luz vertical. El vidrio conserva la muestra del
fondo alineada con su posición y el modo LITE; ahora utiliza un reflejo corto y
suave en el borde superior, sin óvalo blanco sobre las etiquetas ni doble borde.
No se añaden formas decorativas, animaciones continuas ni lecturas de otras apps.

Los iconos de las filas comparten un soporte circular suave de 44 dp. Se retiran
los recuadros y bordes interiores de los iconos y el contenedor rectangular del
modo de activación. La acción principal conserva un azul legible y el botón de
Detener sigue siendo destructivo. La navegación inferior tiene separación lateral
respecto al borde del teléfono. El título protagonista usa el ancho de la tarjeta.
Las flechas, pestañas y gesto del carrusel conservan sus acciones y accesibilidad.

La fuente variable Manrope incluida tenía un peso predeterminado de 200
(ExtraLight), cargado como fuente normal. Se incluyen instancias estáticas de
400, 500, 600 y 700, generadas a partir del archivo existente, y el tema asocia cada
peso a su recurso real. Se conserva la licencia OFL existente. El texto ya no
depende del tipo de letra del teléfono ni del peso ExtraLight por defecto.

El icono vectorial conserva la moto azul y simplifica ruedas, tanque y frente
para que se distingan en tamaño pequeño. La marca aparece en el encabezado. El
icono adaptable usa fondo oscuro sólido y recurso monocromático para Android
compatible. La ventana flotante mantiene sus controles y esfera; el panel
comparte la reducción de reflejo y el borde único del material LITE.

## Validación y capturas

Resultado final: **30 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas**.

Compilan debug, release y fieldTest. Los tres APK se comprobaron como ZIP válidos
con versión `1.1.0-f2-glass3` (sufijo Campo en fieldTest). La compilación incluyó
`lintVitalRelease`, sin errores fatales. No se modificaron dependencias ni ajustes
de compilación del proyecto para adaptar el entorno local.

La regresión de UI incluye 15 pruebas de navegación y protección de Ajustes,
7 del carrusel y acceso a la acción principal, 4 de contraste y 4 de captura.
El caso añadido usa gestos verticales moderados en 320 dp y fuente al 200 %:
comprueba que se alcanza Iniciar, que deslizar no inicia el viaje y que pulsarlo
emite una sola acción y navega a Viaje. No confunde el desplazamiento horizontal
del carrusel con el desplazamiento vertical de la pantalla.

Los contrastes usan los nuevos tintes, la combinación máxima de luces y los
reflejos reales. Las instancias de fuente tienen pesos de metadatos 400, 500,
600 y 700 y no contienen eje variable. El host Compose generó doce vistas de
Inicio, Viaje y Ajustes, en ambos temas y al 100/200 % de fuente. Se revisaron
el material, bordes, iconos, etiquetas y adaptación. Son renders de la UI real
en Android JVM, no fotografías del S22+ ni mockups.

| Vista | Oscuro | Claro |
| --- | --- | --- |
| Inicio | [100 %](images/glass-v3/screens/home-night-100.png) | [100 %](images/glass-v3/screens/home-day-100.png) |
| Viaje | [100 %](images/glass-v3/screens/trip-night-100.png) | [100 %](images/glass-v3/screens/trip-day-100.png) |
| Ajustes | [100 %](images/glass-v3/screens/settings-night-100.png) | [100 %](images/glass-v3/screens/settings-day-100.png) |
| Inicio, letra grande | [200 %](images/glass-v3/screens/home-night-200.png) | [200 %](images/glass-v3/screens/home-day-200.png) |
| Acción alcanzable con letra grande | [320 dp, después de deslizar](images/glass-v3/screens/home-night-200-scrolled.png) | — |

La imagen [de la marca](images/glass-v3/brand-mark.png) representa el recurso
vectorial, no una captura Android. Las capturas `glass-v2` se conservan como
referencia de la versión anterior.

Pruebas físicas pendientes: S22+ en claro y oscuro, fuente al 200 %, TalkBack,
fluidez al desplazar Inicio/Ajustes, burbuja sobre Waze, comando con Y12 Pro y Dime
Evo y recuperación de música. Los servicios, permisos, voz y lógica de navegación
no se modifican en esta revisión visual.

## Revisión en Android Studio

```powershell
git fetch origin
git switch --track origin/feature/ui-storm-refined
```

Si la rama ya existe, usa `git switch feature/ui-storm-refined` seguido de
`git pull --ff-only`. Sincroniza Gradle y ejecuta debug o fieldTest. No mezcles
esta revisión con las ramas históricas retiradas de UI.

## Archivos de la entrega

- `AGENTS.md`
- `app/build.gradle.kts`
- `app/src/main/java/com/gust4av033/appcopilotomoto/OverlayGlassStyle.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/CopilotApp.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/components/GlassControls.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/components/JourneyHero.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/theme/CopilotPalette.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/theme/CopilotTheme.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/theme/Glass.kt`
- `app/src/main/res/drawable/ic_ignitor_vector.xml`
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `app/src/main/res/font/manrope_bold.ttf`
- `app/src/main/res/font/manrope_medium.ttf`
- `app/src/main/res/font/manrope_regular.ttf`
- `app/src/main/res/font/manrope_semibold.ttf`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/values/brand_colors.xml`
- `app/src/test/java/com/gust4av033/appcopilotomoto/HomeDashboardUiTest.kt`
- `app/src/test/java/com/gust4av033/appcopilotomoto/SettingsContrastTest.kt`
- `docs/UI_BASE.md`
- `docs/UI_TORMENTA_REFINADA_V3.md`
- `docs/images/glass-v3/`: marca vectorial y trece capturas Compose.

## Mi ruta con observaciones (actualización de Fase 2)

La misma tarjeta de vidrio de Inicio y Viaje muestra proveedor y campos de
navegación observados con etiquetas explícitas. Cada campo ausente se omite;
un tiempo anterior aparece con su antigüedad. La última solicitud de APP se
muestra por separado y la tarjeta aclara que la lectura no confirma destino
ni ruta activa. Se comprobaron tarjetas sintéticas de Maps y Waze a fuente
150 % en Android JVM; la comparación visual y funcional en el S22+ sigue
pendiente.
