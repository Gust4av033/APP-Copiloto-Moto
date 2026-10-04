# Confirmación de apps y tiempos de WhatsApp · 04-10-2026

Rama: `feature/fase-1-intent-router`. Corrección posterior a Estabilización 1.0.

La confirmación uno/dos usaba exclusivamente una función de MainActivity, retirada al dejar APP en segundo plano. El servicio ahora ejecuta la solicitud confirmada si el teléfono está desbloqueado y el usuario ya concedió **Mostrar sobre otras apps**. Con APP visible conserva la apertura desde Activity. Sin permiso o con pantalla bloqueada, mantiene el aviso; no intenta desbloquear. Limpiar la solicitud ocurre después de terminar la confirmación, evitando invalidar su propio resultado. La apertura sigue limitada a accesos instalados que Android permite abrir; no se verifica el contenido de la app destino.

El TTS tenía un límite de 10 segundos para apps y 28 para leer borradores. El STT imponía 15 segundos desde la preparación, incluso para dictado. Además, la solicitud vencía durante la lectura y existían plazos externos de escucha diferentes. Ahora:

- La inicialización TTS tiene 10 segundos; la reproducción tiene un presupuesto según el texto, entre 15 y 90 segundos. Solo `onDone` permite pasar a escuchar. Error o vencimiento cancela el anuncio.
- La solicitud reserva tiempo para preparación, lectura y respuesta (máximo 180 segundos). Después del anuncio y al estar listo el reconocedor se renueva su aviso: 30 segundos para apps, 60 para borradores. Nunca se revive un token vencido, cancelado o reemplazado.
- STT tiene hasta 10 segundos para preparar. Desde READY dispone de 12 segundos para uno/dos, 40 para dictado y 15 para órdenes. Se eliminan los temporizadores externos contradictorios de apps/dictado. El motor puede terminar antes si detecta silencio; para dictado se sugieren pausas de dos segundos.
- El tono se emite cuando STT está listo, después de acabar TTS. Siguen vigentes los controles de ruta Bluetooth, cancelación, detener y rechazo de callbacks antiguos. «El uno», «número uno» y sus equivalentes para dos también se reconocen, sin aceptar respuestas ambiguas.

WhatsApp conserva el borrador de hasta 240 caracteres y no lo envía automáticamente.

## Prueba física pendiente

Con la moto detenida, instalar el APK de esta revisión, conceder Mostrar sobre otras apps y empezar viaje. Repetir con modo 1 y modo 2, usando la activación configurada.

| Caso | Pasos | Resultado esperado |
| --- | --- | --- |
| APP visible | «Abre [nombre exacto instalado]», esperar anuncio y tono, «uno» | Abre una vez; viaje continúa. Repetir con tres apps. |
| Otra app al frente | Repetir desde Waze/Maps con permiso concedido y teléfono desbloqueado | Solicitud de apertura desde el servicio; destino correcto. |
| Sin permiso / bloqueado | Repetir con APP al fondo sin permiso y con pantalla bloqueada | Conserva aviso; no desbloquea ni cuenta como apertura. |
| Cancelación | «Dos» después del tono | No abre; elimina solicitud; vuelve a espera. |
| Dictado largo | «Escribe a [contacto] en WhatsApp», tono, dictar más de 15 segundos sin superar 240 caracteres | No cancela por el antiguo límite; lee todo el texto y abre una nueva escucha después del tono. |
| Lectura larga | Borrador cercano a 240 caracteres, esperar toda la lectura, «uno» | No vence durante lectura; abre borrador sin enviar. «Dos» lo descarta. |
| Silencio y cierre | Silencio durante anuncio, dictado o confirmación; detener viaje | No hay captura/apertura posterior ni callbacks que revivan la solicitud. Música/ruta se recuperan. |
| Doble confirmación | Después de abrir, decir «uno» otra vez y activar nueva orden | No reutiliza el token ni abre dos veces; nueva orden funciona. |

Registrar frase exacta, modo, teléfono/Android, casco y diagnóstico. La compilación y las pruebas locales no sustituyen esta comprobación de TTS/STT y apertura real en el dispositivo.

Referencia de plataforma: [excepciones para abrir actividades desde segundo plano](https://developer.android.com/guide/components/activities/background-starts), incluyendo el permiso SYSTEM_ALERT_WINDOW ya utilizado por la burbuja.

## Validación local

`clean testDebugUnitTest assembleDebug lintDebug`: compilación correcta, 77 pruebas sin fallos y lint sin errores (55 advertencias y 9 sugerencias). Se comprobó la integridad del APK y el diff. Revisión visible: **Estabilización 02 · apps y tiempos de WhatsApp**. La matriz física anterior queda pendiente.
