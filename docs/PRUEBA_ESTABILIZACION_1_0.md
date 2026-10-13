# Regresión de estabilización 1.0 — S22+ / Android 16 / Y12 Pro

Primero probar detenido. Realizar **dos sesiones independientes**, exportando el reporte de cada una antes de empezar otra. Anotar versión/commit, casco, modo, pantalla y reproductor. El botón físico puede emitir PREVIOUS con una pulsación distinta según el casco: usar la pulsación que en el diagnóstico produjo ese evento.

## Configuración inicial

1. Reproducir una canción en Poweramp y elegirlo en Ajustes → Música.
2. Con viaje detenido, seleccionar **Anterior canción → Escuchar orden**.
3. Abrir **Configurar acceso multimedia**, conceder a APP el acceso a notificaciones y regresar. APP usa las sesiones multimedia, no el contenido de notificaciones.
4. Activar **Solo escuchar al pedir una orden**, o elegir modo 2. Iniciar viaje.
5. Esperar «micrófono cerrado». Usar Pruebas → Reporte para anotar cada intento; esos botones no activan voz. Registrar manualmente los intentos sin detección también.

## Matriz por ronda

| Prueba | Repetición | Resultado exigido |
|---|---:|---|
| PREVIOUS con APP visible | 10 | Una captura por pulsación; no retrocede pista |
| PREVIOUS con Waze al frente | 10 | Captura en servicio sin traer APP al frente |
| PREVIOUS con pantalla bloqueada | 10 | Captura, orden y cierre de micro |
| PREVIOUS mientras prepara/escucha/confirma | 3 | No segunda escucha ni acción diferida |
| Pulsación larga/repeticiones | 3 | Una activación como máximo, no ráfaga |
| «anterior canción», «siguiente», «pausa», «reanudar» | 2 cada una | Control llega a Poweramp; no vuelve a activar APP |
| Controles físicos conservados: siguiente/pausa/volumen | 2 cada uno | Mantienen función; no causan escucha inesperada |
| «pon [título existente]» | 2 títulos | Canción correcta observada, no solo despacho |
| Volumen relativo y porcentaje | 2 | Cambio leído por Android y cambio audible observado |
| Tres órdenes desconocidas/silencio de captura | 1 secuencia | Viaje continúa; admite siguiente activación |
| Llamada saliente: confirmar y cancelar antes de marcar | 1 cada una | No marca al cancelar; recuperación tras terminar llamada |
| «abre [app]»: uno/dos/vencimiento | 1 cada una | Solicitud correspondiente; sin ejecución tardía |
| Chat/borrador WhatsApp y cancelar | 1 cada una | Contacto correcto, texto correcto, mensaje sin enviar |
| Burbuja: mover, minimizar, ocultar | 1 secuencia | No abre micro al mostrarse/moverse; sesión permanece |
| Cambiar modo 1/2 con manual activado | 1 secuencia | Alexa sigue suspendida hasta quitar la opción |
| Quitar manual y probar Alexa | 5 | Modo voz conservado; reconoce y recupera según su perfil |
| Detener viaje con captura activa | 1 | Sin callback posterior, micrófono y captura de botones liberados |
| PREVIOUS fuera de viaje | 3 | Función original de Poweramp; ninguna activación de APP |
| Desactivar reasignación y volver a iniciar | 1 | No captura botones; flujo anterior sigue funcionando |
| Revocar acceso multimedia durante viaje | 1 | Captura suspendida; no recursión de órdenes; usar acceso alternativo |

Para apps y chats, con pantalla desbloqueada y permiso **Mostrar sobre otras apps**, el servicio puede abrir el destino después de «uno» aunque Waze esté al frente. Sin ese permiso, mantener APP visible o usar el aviso. Repetir ambos casos y registrar restricciones del fabricante. Ver [corrección de confirmación y tiempos](CORRECCION_APPS_WHATSAPP_2026-10-04.md).

## Lectura del reporte

- `button_down_received` sin `button_activation`: tecla distinta, repetición/duplicado o sesión ocupada.
- Ningún evento al pulsar PREVIOUS: Android/casco no lo entregó a APP. Revisar diagnóstico y reproducir Poweramp antes de repetir; no concluir que una configuración de software pueda recuperar una señal ausente.
- `stt_started > stt_success`: revisar `stt_failure` y `stt_cancelled`, sin confundir cancelación voluntaria con error.
- `intent_unknown`: hubo texto pero ninguna orden conocida; revisar la última transcripción.
- `action_dispatched`: APP envió solicitud. Confirmar físicamente pista/ventana/cambio antes de anotar `external_effect_annotated`.
- `audio_route_restore_success`: Android informó ruta lista, no es prueba de calidad audible. Anotar por separado `audio_restored_audible_annotated`.
- `session_unexpected_end > 0`: guardar reporte y motivo, resolver antes de promover baseline.

## Registro de resultado

| Campo | Ronda 1 | Ronda 2 |
|---|---|---|
| Commit, fecha, duración | Pendiente | Pendiente |
| Modo/casco/reproductor | Pendiente | Pendiente |
| Activaciones logradas / intentos por escenario | Pendiente | Pendiente |
| Falsos positivos | Pendiente | Pendiente |
| Audio restaurado al escuchar | Pendiente | Pendiente |
| Controles físicos conservados | Pendiente | Pendiente |
| Cierres inesperados y motivo | Pendiente | Pendiente |
| Llamada/apps/burbuja/borrador | Pendiente | Pendiente |
| Reporte copiado | Pendiente | Pendiente |

Promover a baseline solo tras dos rondas sin cierres inesperados, sin funciones sacrificadas fuera del viaje, con audio recuperado y resultados externos comprobados. Si Poweramp/Waze se quedan con el botón, registrar esta vía como experimental/no compatible en ese hardware y conservar el modo botón mediante asistente largo; no afirmar validación D3/D5 por aprobar tests locales.
