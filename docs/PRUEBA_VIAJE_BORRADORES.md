# Prueba de viaje · burbuja y borradores · 02-10-2026

Rama: `feature/fase-1-intent-router`. Revisión visible: **UI burbuja 02 · borradores**. Adelanta solo la preparación de mensajes sobre los chats existentes. Mensajería completa y control de llamadas entrantes corresponden a Fase 5 en la especificación V4.

## Antes de salir, detenido

1. Actualizar rama, compilar e instalar. Configurar casco, contactos para chats, llamadas/estado del teléfono y Poweramp. Usar un contacto único con un solo número internacional.
2. Iniciar modo 2 y habilitar burbuja/permiso si se desea. En Waze: abrir panel, tocar **×** para minimizar, abrir de nuevo y tocar **Ocultar burbuja**. Tocar fuera también debe minimizar y permitir el toque al navegador. Minimizar deja la burbuja; ocultar la quita durante el viaje sin detener audio. Ajustes permite mostrarla otra vez. Detener la retira.
3. Con APP visible y desbloqueada, pedir **«escribe a Ana en WhatsApp»**. Tras anuncio/tono, dictar **«Voy llegando, te aviso cuando llegue»**. Escuchar destinatario, número final y mensaje: **uno** abre el borrador; **dos/cancelar** lo descarta. Para Business, decir **«escribe a Ana en WhatsApp Business»**. No se pide otra activación entre las capturas del flujo.
4. Probar la forma completa **«escribe a Ana en WhatsApp mensaje voy llegando»**. La tarjeta muestra el texto. **No se envía automáticamente**: revisar destinatario/texto y tocar Enviar en WhatsApp, detenido. Si la lectura está mal, decir dos y repetir; aún no hay edición parcial por voz.
5. Durante dictado, **cancelar**, **silencio** o Silencio en notificación descartan; **detener sesión** termina el viaje. El resto del contenido no ejecuta comandos: «sube el volumen» dictado como mensaje queda como texto. Hasta 240 caracteres; texto más largo se rechaza sin recortar. Probar silencio y fallo del STT: recuperar espera y calidad musical.
6. Probar contacto inexistente/ambiguo, permiso denegado y edición no instalada: no elegir otra persona/proveedor. Una respuesta desconocida no abre automáticamente. El plazo de apertura se reserva durante la lectura y se renueva al terminar el anuncio y al estar listo el reconocedor: 60 segundos para borradores y 30 para otras aperturas. Descartar, vencer o detener invalida respuestas/tokens anteriores.
7. Detener durante anuncio, dictado y confirmación: no reactivar micrófono ni abrir chat por callbacks tardíos. Probar Silencio durante anuncio y dictado. Recuperar música normal en modo botón.
8. En segundo plano, probar con el teléfono desbloqueado y el permiso Mostrar sobre otras apps. Sin ese permiso, mantener APP visible o usar la notificación. Con el teléfono bloqueado, la apertura por voz queda pendiente. No se desbloquea el teléfono ni se usa accesibilidad para enviar.

## En el trayecto

Usar funciones ya comprobadas del casco. Probar pantallas, mensajes, llamadas y el modo experimental estando detenido; no revisar/enviar texto mientras se conduce.

| Prueba | Orden | Resultado esperado |
| --- | --- | --- |
| Activación | Alexa en 1; casco/burbuja en 2 | Anotar intentos/aciertos y activaciones sin llamarlo. |
| Música | Siguiente, anterior, pausa, reanuda, «pon [título exacto]» | Pista correcta y recuperación de calidad. |
| Volumen | «sube volumen», «volumen al 40 por ciento» | Cambio audible en la ruta del modo. |
| Modos | Desde 2, «activa modo 1»; tras Alexa, «activa modo 2» | Mismo viaje; micrófono cerrado en espera de 2. Probar 2→3→2 detenido. |
| No entendida | Frase desconocida / silencio | Recupera espera, sin cierre por contador. |
| Apps / chat | «abre Waze», «abre el chat de Ana en WhatsApp» | Uno abre, dos cancela; destino correcto. |
| Llamada saliente | «llama a Ana» → cancelar; después una llamada de prueba acordada | Cancelar no marca; al terminar recupera viaje/audio. |
| Detener | Notificación / «detener sesión» | Sin burbuja ni detector o reapertura tardía. |

**Todavía no disponible:** contestar/rechazar/colgar llamadas entrantes por voz, enviar WhatsApp por voz, leer conversaciones o controlar la navegación de Waze. Cancelar una llamada saliente durante su confirmación evita marcar; no cuelga una llamada conectada. El control físico del casco depende de Android y del casco.

Copiar el diagnóstico de Pruebas y anotar modo/casco, orden exacta, aciertos, falsos positivos, errores/cierres y recuperación musical. Revisar información privada antes de compartirlo.

## Límites de este incremento

El enlace público de WhatsApp agrega texto codificado a un número validado, exclusivamente para la edición solicitada. No confirma entrega ni que el número tenga cuenta. APP conserva el borrador en memoria, sin guardarlo en preferencias/historial. La última frase interpretada puede aparecer en el diagnóstico si incluyó el mensaje en una sola orden. Descartar elimina la solicitud de APP; no borra un borrador ya abierto en WhatsApp.

El servicio reutiliza STT/TTS y la pausa musical. Otras capturas y cambios de modo se bloquean durante dictado/lectura/confirmación. Detener y cancelar invalidan callbacks; un temporizador nunca abre el chat.

Llamadas entrantes necesitan desarrollar recepción, decisión y recuperación del audio. La ruta moderna documentada usa InCallService y un rol de teléfono/acompañante compatible; acceptRingingCall/endCall están deprecadas. Este cambio no sustituye la app Teléfono ni pide permiso de contestación.

Fuentes: [texto prellenado de WhatsApp](https://faq.whatsapp.com/5913398998672934), [InCallService y rol de teléfono](https://developer.android.com/develop/connectivity/telecom/dialer-app), [TelecomManager](https://developer.android.com/reference/android/telecom/TelecomManager).

Validación local: clean, assembleDebug, 59 pruebas unitarias correctas y lintDebug sin errores; 53 advertencias y 9 sugerencias. Se verificó el ZIP del APK y el diff. Las pruebas anteriores en teléfono permanecen pendientes.
