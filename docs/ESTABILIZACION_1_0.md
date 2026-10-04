# Estabilización 1.0 del núcleo manos libres — candidata

Esta iteración aplica el [Documento 04 — Revisión integral del estado, hallazgos y dirección](referencias/Revision_integral_2026-10-03.docx), corte 2026-10-03. Conserva el roadmap de F0/F1; navegación, LLM, lectura de WhatsApp y llamadas entrantes siguen pendientes. Música por nombre y volumen cuentan con la validación del usuario reportada en el historial. La nueva reasignación todavía requiere prueba física.

## Botón configurable

En **Ajustes → Botón dedicado durante el viaje**, el usuario puede dedicar una pulsación de Anterior, Siguiente, Pausa o Reproducir/Pausa a **Escuchar orden**. Por defecto se conservan todos los controles. Para el Y12 Pro se propone `MEDIA_PREVIOUS`, observado en su diagnóstico; no se promete convertir el toque de emparejamiento, que no llegó a Android.

La selección se cambia con el viaje detenido. La captura solo existe durante el viaje; al detenerlo se desactiva y libera la MediaSession. Durante la llamada saliente de APP también se libera; se recupera después de restaurar el audio. La tecla dedicada se consume sin ejecutar su función anterior. El resto de controles multimedia se envía directamente al reproductor. Volumen del teléfono y teclas no multimedia conservan su manejo normal.

Una pulsación DOWN, sin repetición, abre una sola orden. UP, repetición de pulsación larga, entrega duplicada y pulsación mientras APP está ocupada no abren otra captura. No se usa un doble toque ni una ventana de un segundo. La función cedida sigue disponible por voz, por ejemplo **«anterior canción»**.

Para mantener el micrófono cerrado entre órdenes: **modo 2**, o **Solo escuchar al pedir una orden**, que conserva el modo seleccionado y suspende Alexa. La reasignación por sí sola no modifica el modo elegido.

### Acceso y enrutamiento

Android exige acceso a notificaciones para consultar sesiones multimedia de otras apps. Se solicita únicamente desde la configuración de esta capacidad opcional. `TripMediaAccessService` no procesa texto, mensajes ni notificaciones; se usa como componente autorizado para `getActiveSessions`. La selección explícita del reproductor se respeta; si no tiene una sesión activa, se informa el fallo en lugar de cambiar de app silenciosamente.

El reenvío usa MediaController del reproductor, excluye la propia sesión y evita que una orden de voz como «anterior» vuelva a convertirse en activación de APP. Sin reasignación, permanece la vía multimedia anterior.

**Corrección 04-10-2026 — Estabilización 03:** el pulso breve falló en la prueba del usuario: funcionaba una vez sin música y el reproductor recuperaba el botón al sonar/reanudarse. Se sustituye por una salida PCM silenciosa en bucle durante la espera, sin pedir foco ni abrir micrófono/SCO. Se libera antes de STT, cambios de preparación, llamadas salientes y al cerrar el viaje. Android 13+ permite consultar y observar la sesión destinataria; APP verifica el token real y limita la recuperación a tres intentos por pérdida. Si Android conserva otro destinatario, lo informa en el diagnóstico. Puede mantener activa la salida multimedia y aumentar el consumo; sigue pendiente de prueba física de calidad/consumo y recepción con el casco. Los controles restantes se reenvían directamente al reproductor. No se usa accesibilidad ni se modifica el firmware del casco. Ver [regresión y APK de prueba](CORRECCION_BOTON_MUSICA_2026-10-04.md).

Referencia Android: https://developer.android.com/media/legacy/media-buttons

## Estabilización y extracción gradual

- `TripButtonCoordinator` es dueño de la MediaSession, salida silenciosa y observación del destinatario de botones. Limpia su puente a la Activity y tareas pendientes al cerrar.
- `TripButtonPolicy` concentra selección, duplicados, repeticiones y espera ocupada.
- `TripCaptureCoordinator` registra las capturas, invalida callbacks tras cancelar y conserva la política de reanudación existente.
- `TripTestState` deja de estar embebido en `TripVoiceService`; mantiene el estado de presentación y persistencia.
- Al detener el viaje, una restauración de ruta fallida se registra y **no reanuda música sobre una ruta todavía degradada**. La ruta normal informada por Android se distingue de la calidad audible.
- Se conservan los tokens de acciones pendientes, vencimientos independientes, recuperación de llamadas y cancelación de borradores. No se reescribe el servicio completo.

## Métricas exportables

**Pruebas → Reporte de diagnóstico → Copiar reporte** incluye contadores por etapa, contexto de la captura (modo, activación manual, bloqueo, visibilidad y ruta/casco), latencia STT, latencias existentes y motivo del último cierre inesperado.

| Grupo | Evidencia |
|---|---|
| Intentos y falsos positivos | Anotación manual; APP no puede observar intentos que nunca detectó |
| Activación | Wake aceptado, petición manual y botones DOWN recibidos/activadores |
| STT | Iniciado, texto recibido, error o cancelación |
| Router | Intención principal resuelta o desconocida |
| Acción | Solicitud despachada; no implica resultado externo |
| Confirmación automática | Cambio de volumen leído por Android; no confirma intensidad audible |
| Confirmación externa | Anotación del usuario de canción/app/efecto observado |
| Audio | Ruta lista/no lista; recuperación audible anotada por separado |
| Sesión | Cierre no solicitado con motivo; interrupción de proceso detectada al volver a abrir |

Las capturas de dictado y confirmación también cuentan como STT; sus respuestas no se suman como nuevas órdenes del router. Los contadores antiguos se conservan para continuidad, pero no reemplazan este pipeline. Los nuevos contadores se reinician al comenzar un viaje. **Copiar el reporte antes de iniciar otro**. No se guardan grabaciones ni se acumula un historial de viajes en esta iteración.

## Madurez y estrategia Git

| Estado | Criterio |
|---|---|
| D0 | Diseño documentado |
| D1 | Código integrado en rama de trabajo |
| D2 | Compilación, pruebas y lint aprobados |
| D3 | Prueba en S22+/Y12 |
| D4 | Prueba en campo |
| D5 | Regresión repetida y baseline estable |

Esta entrega es candidata de estabilización de `feature/fase-1-intent-router`. No marca reasignación, accesos multimedia ni métricas como D3–D5 antes de la prueba del usuario. `main` conserva su estado anterior hasta cumplir la matriz; no se presenta su base temprana como versión actual del producto. No continuar en las ramas antiguas de F0/UI para estas funciones. Al aprobar dos rondas y revisar el diff de F1, integrar la candidata en main y etiquetar la baseline, sin mezclar navegación en esa integración.

Guía y criterios: [PRUEBA_ESTABILIZACION_1_0.md](PRUEBA_ESTABILIZACION_1_0.md).

## Verificación local de esta entrega

`testDebugUnitTest`, `assembleDebug` y `lintDebug` aprobados. **70 tests, 0 fallos, 0 errores de lint**. Se comprobaron también los cambios finales de reenvío al reproductor. Existen avisos/hints de APIs deprecadas y estilo; lint sin errores no significa ausencia de avisos. La reasignación y el nuevo pipeline alcanzan **D2**, pendientes de la matriz física D3/D4 y de repetición D5.
