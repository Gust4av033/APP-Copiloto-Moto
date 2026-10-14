## Evolución conjunta de lógica y UI en APP

- Cada cambio funcional debe revisar su efecto en pantallas, ajustes, permisos,
  comandos, burbuja, notificación y feedback de voz. Actualiza lo afectado en
  la misma entrega; si no tiene efecto visible, explica brevemente por qué.
- Consulta la documentación de UI vigente. Conserva el sistema de diseño y
  reutiliza componentes y recursos. No cambies dependencias por decoración.
- La dirección visual actual es vidrio tormenta medianoche, siguiendo
  docs/UI_TORMENTA_MEDIANOCHE_V2.md. Mantén texto nítido, iconos coherentes y contraste.
  Representa la profundidad sin efectos que degraden la fluidez o el audio.
- Revisa colores semánticos de contenido, inputs y botones. Los controles
  principales necesitan identificación clara y selección/estado visible.
  Comprueba el acabado en capturas reales cuando se pueda renderizar.
- La UI observa estado real compartido y emite eventos. No dupliques reglas
  del servicio ni actives micrófono o acciones al dibujar una pantalla.
- Distingue modo de activación, estado de voz, sesión, audio y acción pendiente.
  Inicio, Viaje, burbuja y notificación deben ser consistentes.
- Cada control debe tener soporte funcional, estado, requisitos y recuperación.
  Representa experimental/futuro cuando corresponda. No uses datos ficticios
  fuera de previews y escenarios de prueba identificados.
- Distingue solicitud despachada de efecto confirmado en apps externas.
- Mantén el navegador al frente durante ruta y preserva sesión y recuperación
  de audio. Mostrar, mover u ocultar la burbuja no debe activar el micrófono.
- Agrupa ajustes en subpantallas; solicita permisos por función. Usa textos
  localizables, tamaños táctiles adecuados y estados que no dependan del color.
- Verifica compilación y comprobaciones pertinentes. Inspecciona UI renderizada
  cuando sea posible. Declara por separado las pruebas físicas pendientes.
- Actualiza la trazabilidad de capacidad, UI y prueba en los documentos existentes.
  Conserva cambios locales y usa la baseline vigente, sin asumir ramas históricas.

## Acabado visual de vidrio

- Dirección: vidrio oscuro sobre fondo "tormenta medianoche" (base #040A1C, azul
  eléctrico #2F6BFF, luz #79DDFF). Un solo elemento protagonista por pantalla;
  lo demás, vidrio sobrio.
- El vidrio es: fondo propio con formas luminosas de borde definido, versión
  desenfocada precalculada de ese fondo dibujada dentro de cada panel según su
  posición, tinte oscuro para el contraste, borde de luz en gradiente y brillo
  especular. No leas la pantalla ni desenfoques controles. Sin `shadowElevation`
  en superficies translúcidas.
- Todos los valores (colores, alphas, radios, bordes) viven en `theme/Glass.kt` y
  `CopilotPalette`; las pantallas no llevan valores sueltos. Cualquier cambio
  compartido se replica en la burbuja nativa (modo LITE).
- Botones con tres jerarquías: primario, secundario y destructivo. Detener o
  cancelar nunca se ve igual que la acción principal.
- Atrás, siguiente y cerrar son `GlassIconButton` de al menos 48dp con flecha. No
  uses `TextButton` para navegar. Carruseles y pasos llevan indicador de página.
- Iconos centrados con `Box(contentAlignment = Center)` y un `IconTile` único.
  Espaciados en múltiplos de 4dp.
- El tema define una `FontFamily` explícita.
- Movimiento solo como respuesta a una acción, corto y respetando las
  animaciones del sistema. Nada continuo en Viaje.
- Rendimiento: nada se recalcula por frame; debe existir modo LITE para
  desactivar el desenfoque.
- Antes de cerrar: compilar, correr `SettingsContrastTest` y los tests de UI, y
  revisar capturas en oscuro, claro y fuente al 200%. La deuda visual fuera del
  alcance se anota en los documentos de trazabilidad.
