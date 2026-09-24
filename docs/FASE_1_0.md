# Fase 1.0 — primera prueba de llamadas por voz

Esta prueba reutiliza la sesión de viaje de la fase 0.3 y conserva los comandos de música y volumen. La alternativa de micrófono del teléfono para «Alexa» continúa siendo opcional y no ha sido validada en el casco del usuario; seleccionar **Casco: activación y orden** mantiene el recorrido previamente probado.

## Llamar por nombre sin selección previa

Antes de iniciar la sesión, pulsar **Habilitar llamadas por nombre** y conceder `READ_CONTACTS` y `CALL_PHONE`. El acceso a toda la agenda solo se utiliza para buscar localmente el nombre pronunciado; APP no sube contactos ni guarda nombres o números de búsquedas por voz. Durante la sesión, decir «Alexa», esperar el tono y luego «llama a Ana». El detector y el STT son etapas separadas: una frase seguida sin pausa («Alexa llama a Ana») podría perder las palabras posteriores a «Alexa».

APP busca primero un nombre completo exacto, después un comienzo de nombre que sea único; si hay varias personas coincidentes o más de un número para esa persona, **no llama**. En caso de una sola coincidencia muestra el nombre y los últimos cuatro dígitos, **dice en voz alta el nombre** y da un segundo tono antes de escuchar «confirmar llamada» o «cancelar». Si el motor de voz no puede anunciar el destinatario, cancela la llamada. La ausencia de confirmación tampoco marca. La sesión se cierra antes de entregar la solicitud de llamada al sistema.

Validar con el teléfono y la moto detenidos: probar nombre inexistente, un nombre ambiguo, «cancelar», el tiempo de espera y una llamada confirmada a alguien que espere recibirla. Comprobar que Android use el número correcto y el casco durante la conversación. Sin permisos, música y volumen siguen disponibles.

## Preparación en la app visible

1. Para la ruta **opcional de contacto fijo**, seleccionar **Elegir contacto para llamada**. El selector de Android entrega un único número sin conceder a APP acceso a toda la agenda. Puede usarse cuando un nombre o número es ambiguo.
2. Conceder `CALL_PHONE` cuando se solicite. Es independiente del permiso de micrófono. El contacto elegido queda guardado en preferencias privadas de la app, el número se muestra enmascarado y se puede cambiar o quitar con la sesión detenida. Sin contacto o permiso, la orden «llamar» no marca.
3. Iniciar la sesión de viaje con el casco conectado. La orden sin nombre «llamar» usa exclusivamente el contacto fijo; «llama a [nombre]» usa la agenda con sus permisos y requiere coincidencia única.

## Secuencia

Decir **«Alexa»**, esperar el primer tono y decir **«llamar»**. Esperar el segundo tono y decir **«confirmar llamada»** o **«cancelar»**. Cualquier otra respuesta o doce segundos sin confirmación evita marcar y devuelve el detector a «Alexa». Solo tras la segunda orden APP libera la captura de voz, restaura la ruta de audio y solicita a Android iniciar una llamada SIM al contacto seleccionado mediante `TelecomManager.placeCall`. La sesión de viaje y su notificación terminan para ceder el control de audio a la app Teléfono; podría aparecer un selector de SIM si el dispositivo lo requiere.

El contador de acciones incluye solicitudes de llamada entregadas a Telecom, no confirma que el teléfono conectó la llamada. La app no graba voz ni guarda la transcripción de la confirmación.

## Validación estacionaria en el S22+

- Elegir un número de prueba propio o de una persona que espere la llamada; verificar el número en la pantalla antes de iniciar.
- Con música sonando, decir «Alexa» → «llamar» → «cancelar»: no debe iniciar ninguna llamada y la sesión debe seguir escuchando.
- Repetir y decir «confirmar llamada»: la sesión y notificación deben cerrarse y Android debe mostrar la llamada al número elegido. Confirmar que se oye por el casco y se puede colgar desde el teléfono/casco.
- Probar una confirmación diferente y esperar doce segundos: no se debe iniciar llamada. Repetir con permiso de llamada denegado para ver el mensaje de configuración.
- Después de terminar la llamada, iniciar una nueva sesión de viaje manualmente; no reactivar el micrófono automáticamente.

## WhatsApp

Los enlaces oficiales de WhatsApp pueden abrir un chat con texto rellenado, pero exigen que el usuario envíe el mensaje en WhatsApp. Esta prueba no simula toques, no usa accesibilidad para pulsar **Enviar** ni promete envío sin pantalla. Un flujo de dictado y revisión del borrador podrá añadirse cuando se defina una experiencia útil y segura para el viaje.
