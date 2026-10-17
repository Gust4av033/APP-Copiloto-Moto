## Evolución conjunta de lógica y UI en APP

- Cada cambio funcional debe revisar su efecto en pantallas, ajustes, permisos,
  comandos, burbuja, notificación y feedback de voz. Actualiza lo afectado en
  la misma entrega; si no tiene efecto visible, explica brevemente por qué.
- Consulta la documentación de UI vigente. Conserva el sistema de diseño y
  reutiliza componentes y recursos. No cambies dependencias por decoración.
- La dirección visual actual es vidrio tormenta medianoche, siguiendo
  docs/UI_TORMENTA_REFINADA_V3.md. Mantén texto nítido, iconos coherentes y contraste.
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

- Dirección: vidrio oscuro sobre fondo azul profundo con luz difusa (base #050C1D, azul
  eléctrico #2F6BFF, luz #79DDFF). Un solo elemento protagonista por pantalla;
  lo demás, vidrio sobrio.
- El vidrio es: fondo propio con luces azules suaves, versión
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

## Contexto de viaje y Fusión por Capas

1. Capa 1: GPS y sensores propios del teléfono, solo con permiso y ajuste activo.
2. Capa 2: calle bajo las ruedas, reservada para un motor libre futuro como OSM/Valhalla.
3. Capa 3: ruta propia estimada a las coordenadas del destino; por ahora solo distancia lineal y rumbo, nunca una ruta vial supuesta.
4. Capa 4: observación del navegador externo Waze o Google Maps por notificación y, en el sabor autorizado, accesibilidad. Una solicitud despachada no confirma la ruta.
5. Capa 5: futuros servicios de tráfico de pago, sin implementación ni acceso de red en Fase 2.

Todo dato del contexto lleva `source`, captura monotónica, confianza entre 0 y 1, `ttlMs` e `isSimulated`. La fusión descarta datos vencidos y prefiere los frescos de mayor confianza. Sin permiso o con el ajuste apagado, no se lee ni guarda GPS. Replay jamás toca el historial real. Ningún dato de viaje sale del teléfono. El detector de movimiento funciona solo en sombra: registra eventos, sin preguntas espontáneas.

## Ciclo de audio de las órdenes

- Una orden, su dictado y sus confirmaciones forman un solo ciclo de audio. Mantén la música que APP pausó en pausa hasta completar, cancelar o agotar ese ciclo; conserva un único foco de audio entre turnos.
- Reanuda como máximo una vez después de restaurar la ruta del casco, solo si APP pausó la reproducción. Una búsqueda musical enviada, una pausa pedida por el usuario o una llamada en curso no autorizan PLAY. El ciclo tiene límite de inactividad y diagnóstico sin datos personales.

## Reproductor durante el viaje

- Para buscar o controlar música, prioriza el reproductor nombrado en la orden, el elegido temporalmente en este viaje, una sesión multimedia que suena y finalmente Ajustes. Abrir un reproductor por voz puede elegirlo para esa sesión sin cambiar la preferencia guardada.
- Solo usa `playFromSearch` si la sesión anuncia esa acción; Poweramp conserva su receptor directo y el intent estándar exige APP visible y teléfono desbloqueado. Enviar búsqueda o controles no confirma reproducción. No infieras la app al frente sin un acceso específico.

- La búsqueda de POI registra una solicitud despachada, nunca un resultado o ruta confirmada. Clima y resultados propios quedan bajo `WeatherProvider` y `PlacesProvider` de Fase 7; no inferirlos de pantallas ajenas.
