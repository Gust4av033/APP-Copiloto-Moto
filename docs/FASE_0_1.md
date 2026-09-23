# Fase 0.1 — reconocimiento y orden multimedia de prueba

La pantalla incorpora una prueba de una sola orden: botón **Escuchar orden** -> reconocedor local de Android -> regla determinista -> evento multimedia. La escucha comienza al pulsar el botón. Todavía no hay detector de frase de activación ni servicio de viaje.

## Probar en el S22+ con el casco

1. Actualizar la rama `feature/fase-0-audio-bluetooth` y ejecutar la app.
2. Conectar el intercomunicador y reproducir música en una app que acepte el botón **Siguiente** del casco. Dejar la música sonando.
3. Pulsar **Escuchar orden** y decir «siguiente canción» por el casco; anotar el texto reconocido, la latencia y si realmente cambió la pista.
4. Repetir tres veces con el teléfono alejado. La prueba anterior confirmó la ruta de `AudioRecord`, pero el reconocedor de Android usa su propio servicio: comprobar físicamente que toma la voz del casco.
5. Repetir «cancelar», «cállate» y una frase desconocida. Ninguna debe saltar una pista. **Cancelar escucha** debe detener la escucha pendiente sin ejecutar acciones.
6. Si sale «No hay reconocimiento de voz local disponible» o un código de error, anotar el código y revisar que el teléfono tenga instalado el reconocimiento de voz en español sin conexión. Esta prueba no usa un reconocedor remoto como alternativa automática.

El mensaje «Envié Siguiente canción» significa que Android aceptó el envío de la tecla multimedia; confirmar el cambio efectivo de pista oyendo la música o viendo la app. La cifra de latencia mide desde el botón hasta recibir el resultado, y no incluye wake word. `STOP_TTS` solo se reconoce como intención: aún no hay voz sintética que detener.

Probar estacionado. El siguiente tramo integrará un wake word real y un ciclo visible de escucha continua para validar la interacción manos libres sin tocar la pantalla.
