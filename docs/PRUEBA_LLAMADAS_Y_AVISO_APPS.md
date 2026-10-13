# Recuperación de viaje y apertura emergente

Rama de trabajo única: `feature/fase-1-intent-router`. Incluye recuperación de llamadas, aviso emergente y correcciones del modo botón. La validación de Bluetooth requiere S22+ y Y12 Pro.

En Ajustes habilitar llamadas por nombre para conceder Contactos, Teléfono y Estado del teléfono. También elegir de nuevo el contacto de prueba concede los dos permisos de teléfono.

Probar cada escenario tanto con Alexa como con botón del casco, con S22+ y Y12 Pro:

1. Iniciar viaje, pedir una canción, solicitar una llamada y decir cancelar. Debe permanecer activo, recuperar la ruta y aceptar otra orden de música y volumen.
2. Solicitar una llamada y no responder a la confirmación. A los 12 segundos debe volver a espera sin marcar.
3. Confirmar llamada. El viaje permanece activo en espera y el micrófono del asistente se cierra. Terminar la llamada desde Teléfono; tras recuperar audio debe aceptar otra activación sin reiniciar viaje.
4. Repetir cancelando la llamada desde Teléfono antes de que la otra persona conteste. Debe volver al viaje. Si Android no inicia la llamada ni informa un estado activo, se comprueba el estado inactivo tras 30 segundos.
5. Detener viaje durante una llamada: finalizar la llamada no debe reactivar el viaje.
6. Pedir abre [app instalada] con pantalla encendida y otra app visible. Debe aparecer el aviso Abrir [nombre], tocable directamente. Configurar Android → notificaciones de APP → Abrir aplicaciones → permitir avisos emergentes si no aparece. No molestar y pantalla bloqueada pueden modificar su presentación. No se fuerza desbloqueo.
7. Pedir una segunda app: sustituye el aviso anterior. Decir cancelar o cualquier nueva orden elimina el aviso. Un nombre inexistente no deja el aviso anterior.
8. Repetir música, pausa, volumen y detener sesión. Registrar si la calidad del audio vuelve, si la sesión se mantiene y si aparecen falsos positivos.

La sesión utiliza un canal silencioso y la selección de apps un canal independiente de importancia alta. El sistema controla la presentación del aviso. La restauración de llamadas usa el estado de telefonía del dispositivo; no valida llamadas de apps VoIP.


## Regresión del modo botón

- Repetir 10 órdenes con pantalla bloqueada. Tras cada orden debe volver a esperar el botón, con música por la salida normal y micrófono cerrado.
- No hablar después de pulsar: si el reconocedor no responde, a los 15 segundos se cancela la captura y se restaura el audio; la siguiente pulsación debe funcionar.
- Cancelar una llamada por voz y por Silencio durante el anuncio. La música no debe reanudarse entre la primera orden y la confirmación mientras SCO siga seleccionado.
- Decir pausa y después una orden inválida: la música debe permanecer pausada.
- Al terminar la llamada, APP espera que Android libere el audio del teléfono antes de recrear el detector. Una devolución de audio que no termina deja un mensaje explícito.

Pruebas automáticas: SpeechPlaybackStateTest cubre transferencia de pausa a confirmación, pausa explícita, errores con música previamente pausada y consumo único de reanudación.

Validación del 30-09-2026: `:app:testDebugUnitTest` (18 pruebas, 0 fallos, 0 errores) y `:app:assembleDebug` finalizaron correctamente. La prueba física del audio y botón sigue pendiente.
