# Fase 0.2 — detección local de la frase de activación

Este prototipo usa `WakeWordProvider` con una implementación local de openWakeWord para la frase temporal **«Hey Jarvis»**. El paquete Android incluye el detector y sus modelos ONNX. Al detectarla, APP pausa la captura del wake word, inicia el STT local para una orden corta, ejecuta las reglas existentes y vuelve a esperar la frase. Ambos motores no escuchan a la vez. La prueba se inicia y detiene explícitamente desde la app visible.

## Prueba controlada

1. En la rama `feature/fase-0-audio-bluetooth`, ejecutar `git pull`, sincronizar Gradle (la primera sincronización descarga una biblioteca nueva) e instalar en el S22+.
2. Emparejar el casco, iniciar música y dejar la app visible. La sensibilidad comienza en **75 %** (umbral interno 0,25, antes 0,50). Pulsar **Iniciar activación**.
3. Decir **«Hey Jarvis»** por el casco. Esperar a que el estado diga **Preparando reconocimiento**, y luego decir **«siguiente canción»**. Comprobar que el texto reconocido es correcto y que la música cambió de pista.
4. Repetir diez intentos a distancias y volúmenes de voz normales, dejando el teléfono lejos del casco. Anotar intentos pronunciados, activaciones detectadas, órdenes reconocidas y cambios efectivos de pista. Observar durante cinco minutos sin decir la frase y anotar activaciones espontáneas. Si sigue perdiendo activaciones, detener la sesión, subir la sensibilidad a 80 % y repetir la medición. Si hay activaciones falsas, bajarla a 70 % y repetir. El ajuste se aplica al iniciar una sesión nueva.
5. Probar **Detener sesión** mientras espera la frase y durante la escucha de la orden. La captura debe terminar y no debe ejecutar ninguna acción pendiente.
6. Hacer la prueba estacionado, primero con motor apagado. Con motor encendido, mantener la moto estacionada y registrar los resultados por separado.

La cifra **Activaciones detectadas** por sí sola no demuestra tasa de acierto: compararla con los intentos pronunciados y con las activaciones falsas. La cifra de la fase 0.1 sigue midiendo desde el inicio del STT hasta el envío de la tecla; aún no mide wake -> acción de forma completa. El servicio de sesión y la notificación persistente llegarán en el siguiente tramo, antes de probar con la pantalla apagada.

El control de sensibilidad modifica el umbral del detector (sensibilidad 75 % = umbral 0,25); no es un porcentaje de acierto. El reconocimiento de «siguiente canción» usa otro motor, así que acertar esa orden no garantiza que el modelo de «Hey Jarvis» reconozca bien la frase por el casco.

## Alcance del modelo

«Hey Jarvis» sirve solo para validar el flujo técnico; la frase personalizable requiere un modelo acústico propio y pruebas de sensibilidad. El motor es sustituible a través de `WakeWordProvider`. Los modelos preentrenados de openWakeWord tienen licencia **CC BY-NC-SA 4.0**: antes de distribuir comercialmente la app deberá revisarse o reemplazarse esa dependencia/modelo. No se guarda audio ni se carga a una nube.
