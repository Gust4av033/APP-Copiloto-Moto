# Fase 0.0 — diagnóstico de audio Bluetooth

Esta primera entrega valida la ruta de entrada del micrófono. Es una prueba de cinco segundos dentro de la app; todavía no activa una escucha permanente ni una sesión de viaje.

## Qué mide

- Entrada Bluetooth solicitada y dispositivo que `AudioRecord.getRoutedDevice()` reporta mientras graba.
- Dispositivo de comunicación seleccionado por Android (no garantiza por sí solo dónde se oyó el audio).
- Muestras recibidas, duración, nivel medio y pico de la señal. El audio se descarta inmediatamente y no se sube al repositorio.
- Aviso cuando Android no confirma el micrófono del casco o la señal es muy baja.

## Prueba en el S22+

1. Conectar el casco/intercomunicador en Ajustes de Bluetooth y comprobar que ofrece perfil de llamadas/micrófono, además de música si corresponde.
2. Actualizar la rama `feature/fase-0-audio-bluetooth` en Android Studio, sincronizar Gradle e instalar `app` en el S22+.
3. Abrir APP, pulsar **Iniciar prueba** y conceder permisos de **Micrófono** y **Dispositivos cercanos** cuando el sistema los solicite.
4. Hablar durante los cinco segundos. Anotar entrada solicitada, entrada real, muestras, duración, nivel medio y el aviso si aparece.
5. Repetir con el casco desconectado y después con el casco conectado, hablando cerca del teléfono y luego solo cerca del micrófono del casco. Esto ayuda a comprobar de dónde procede la señal.
6. Repetir tres veces con moto apagada y luego, de forma segura y estacionado, tres veces con el motor encendido. Registrar los resultados sin conducir durante la prueba.

Si Android informa `Micrófono del teléfono` aunque el casco esté conectado, o la señal no cambia cuando se habla cerca del casco, aún no queda validada la captura Bluetooth. Registrar versión de Android, modelo del intercomunicador y resultados para ajustar el enrutamiento.

## Próximas piezas de Fase 0

Después de confirmar el audio del casco: detector de frase de activación real, STT de una sola orden, comandos `MEDIA_NEXT` / `STOP_TTS` / `CANCEL`, servicio visible de sesión y métricas de extremo a extremo. El motor de wake word se elegirá mediante una prueba real en el teléfono; esta pantalla no se presenta como un detector de activación.
