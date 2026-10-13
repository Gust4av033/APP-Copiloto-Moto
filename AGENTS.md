## Evolución conjunta de lógica y UI en APP

- Cada cambio funcional debe revisar su efecto en pantallas, ajustes, permisos,
  comandos, burbuja, notificación y feedback de voz. Actualiza lo afectado en
  la misma entrega; si no tiene efecto visible, explica brevemente por qué.
- Consulta la documentación de UI vigente. Conserva el sistema de diseño y
  reutiliza componentes y recursos. No cambies dependencias por decoración.
- La dirección visual es vidrio esmerilado sobre un fondo propio morado,
  celeste y azul. Mantén texto nítido, iconos vectoriales coherentes y contraste.
  Representa la profundidad sin efectos que degraden la fluidez o el audio.
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
