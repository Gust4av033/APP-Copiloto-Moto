## Evolución conjunta de lógica y UI en APP

- Cada cambio funcional debe revisar su efecto en pantallas, ajustes, permisos,
  comandos, burbuja, notificación y feedback de voz. Actualiza lo afectado en
  la misma entrega; si no tiene efecto visible, explica brevemente por qué.
- Consulta la documentación de UI vigente. Conserva el sistema de diseño y
  reutiliza componentes y recursos. No cambies dependencias por decoración.
- La dirección visual es vidrio esmerilado sobre un fondo propio morado,
  celeste y azul. Mantén texto nítido, iconos vectoriales coherentes y contraste.
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
El vidrio se construye con capas, sin blur sobre contenido ni dependencias:
tinte translúcido en gradiente vertical, borde de 1dp con gradiente, brillo
especular en el borde superior y resplandor solo en el control principal.
Todos esos valores viven en theme/Glass.kt; las pantallas no llevan colores,
alphas ni radios sueltos.
Un solo acento brillante por pantalla (la acción o el estado principal). Lo
demás es vidrio sobrio. Si un elemento no informa ni da identidad, se quita.
Botones con tres jerarquías: primario, secundario y destructivo. Detener o
cancelar nunca se ve igual que la acción principal.
Atrás, siguiente y cerrar son GlassIconButton de al menos 48dp con flecha.
No uses TextButton para navegar. Los carruseles y pasos llevan indicador de
página.
Alineación: los iconos van centrados con Box(contentAlignment = Center), con
un IconTile único para todas las filas y tarjetas. Espaciados en múltiplos
de 4dp. Elementos del mismo nivel comparten radio, alto de icono y padding.
Tipografía: el tema define una FontFamily explícita para que la app se vea
igual en cualquier teléfono. Pesos y tamaños salen de la escala del tema.
Movimiento solo como respuesta a una acción (presionar, cambiar de estado),
corto y respetando las animaciones del sistema. Nada continuo que distraiga
en Viaje.
Si cambias un color o token compartido, revisa también la burbuja nativa
(OverlayGlassStyle.kt, TripOverlay.kt).
Antes de cerrar: compilar, correr SettingsContrastTest y los tests de UI, y
revisar capturas en oscuro, claro y fuente al 200%. La deuda visual que veas
fuera del alcance de la tarea se anota en los documentos de trazabilidad; no
se rediseña en la misma entrega.
