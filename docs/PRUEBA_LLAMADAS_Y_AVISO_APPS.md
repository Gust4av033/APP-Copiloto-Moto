# Recuperación de viaje y apertura emergente

Cambio basado en Fase 1 37ca4a6. Pendiente de compilación y validación física.

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
