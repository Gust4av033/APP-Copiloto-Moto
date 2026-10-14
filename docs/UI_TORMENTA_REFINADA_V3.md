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

## Estado de validación

Pendiente de cerrar la compilación, contraste y pruebas de UI de esta revisión.
Las capturas de `glass-v2` son evidencia de la versión anterior, no de v3.
La imagen `images/glass-v3/brand-mark.png` representa el recurso vectorial, no una
captura Android. No se atribuyen pruebas previas a esta entrega.

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
