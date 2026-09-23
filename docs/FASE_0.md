# Fase 0 — Validación manos libres

## Objetivo

Probar que el copiloto puede recibir una orden corta con casco y ruido real, ejecutar «siguiente canción» repetidamente y registrar el resultado. La pantalla inicial del repositorio es solo una base de compilación.

## Entregables por orden

1. Medir entrada/salida de audio con micrófono Bluetooth y registrar el dispositivo realmente seleccionado.
2. Integrar un `WakeWordProvider` de prueba con una frase/modelo conocido. La frase arbitraria personalizable se validará después.
3. Escuchar un comando corto con `SpeechToTextProvider` tras la activación.
4. Resolver `MEDIA_NEXT`, `STOP_TTS` y `CANCEL` mediante reglas deterministas.
5. Ejecutar la acción multimedia a través de un adaptador Android y devolver un resultado explícito.
6. Mantener una sesión de prueba visible para el usuario con los mecanismos permitidos por la versión de Android.
7. Medir activaciones correctas, falsos positivos, fallos de STT, resultado de la acción y latencia extremo a extremo.

## Criterio de validación

Pruebas con el Samsung S22+ y el casco/intercomunicador, primero en un entorno controlado y luego con ruido representativo. Registrar los valores obtenidos antes de fijar umbrales de aceptación. Comprobar que silencio/cancelar interrumpe la salida y que la sesión continúa sin reabrir manualmente la app.

## Límites de esta fase

Room completo, identidad Google, sincronización, Waze, LLM, rutas y SOS se aplazan. Los permisos se añadirán al implementar cada función con su explicación contextual. Ninguna credencial o grabación personal se versiona.

