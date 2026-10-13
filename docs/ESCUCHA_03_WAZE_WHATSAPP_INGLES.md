# Escucha 03 · Waze, borradores e inglés

## Evidencia de campo y alcance

El 12 de octubre de 2026 el usuario confirma llamadas funcionales y buen funcionamiento general. Reporta que Waze anuncia Casa pero no escucha la confirmación, que los mensajes siguen fallando y que los títulos musicales en inglés necesitan mejorar. Se conserva la UI ruta 02 y el flujo de llamadas salientes.

No se ha reproducido el fallo físico del Y12 en este entorno. El código anterior abría el segundo reconocedor 300 ms después de TTS sin volver a seleccionar ni esperar la ruta del casco. La nueva preparación vuelve a seleccionar el dispositivo y exige dos lecturas consecutivas de ruta disponible antes de abrir STT. Si falla, conserva el motivo en Último resultado; el viaje regresa a espera. Esta corrección aborda una transición vulnerable, sin atribuir definitivamente el fallo reportado al casco, TTS o Android.

## Cambios

- Preparación común después de TTS para confirmaciones de apps/Waze, dictado, relectura y títulos en inglés. Espera de drenaje de 300 ms y plazo de ruta de 4 s; cancelación o solicitud reemplazada invalida tareas anteriores.
- Confirmación y dictado conservan sus ventanas de 18 y 40 s desde READY. El dictado puede reabrirse hasta dos veces por silencio temprano sin voz detectada, dentro del mismo plazo. No se concatenan resultados ni se reinicia un texto ya reconocido.
- Un evento de inicio de voz durante los primeros 400 ms del tono no bloquea por sí solo la recuperación por silencio. Palabras parciales sí cuentan como voz, incluso en ese intervalo. La audibilidad real y el comportamiento del motor Samsung requieren prueba física.
- Las renovaciones del aviso de apertura no vuelven a disparar su alerta sonora. Los errores de reconocimiento se muestran con etapa, idioma, estado READY y entrada informada.
- «Envía/manda un mensaje a Ana por WhatsApp» y «Envía un mensaje por WhatsApp a Ana» preparan el mismo borrador revisable que «escribe». No se implementa envío automático.
- «Busca canción en inglés» o «pon una canción en inglés» abre una segunda captura local `en-US` después del anuncio y tono. El título se conserva como dato y se busca en el reproductor elegido. Las llamadas, dictado y demás órdenes permanecen en español. Se informa si el modelo inglés no está disponible; no hay descarga automática ni uso de reconocimiento en nube.

## Prueba en S22+/Y12, detenido

Usar la instalación habitual actualizada en `feature/fase-1-intent-router`. Debe mostrar **Escucha 03**. Mantener Anterior → Escuchar orden, acceso multimedia y Mostrar sobre otras apps; teléfono desbloqueado para abrir Waze/WhatsApp. Evitar otro viaje activo en la copia de prueba.

1. Con música, pedir «vamos a casa». Esperar anuncio completo y tono; responder «uno». Confirmar que abre Waze con Casa. Repetir con «dos» y verificar que no abre. Repetir tres veces, incluyendo una respuesta unos segundos después del tono.
2. Pedir «envía un mensaje a [contacto] por WhatsApp». Tras el tono, dictar «Estoy llegando en diez minutos». Tras la lectura y el siguiente tono, responder «cuatro», luego «tres», dictar un texto nuevo y elegir «uno». Comprobar destinatario y texto nuevo en WhatsApp. El envío continúa manual.
3. Repetir con un mensaje breve pero más largo, de menos de 240 caracteres. Comprobar que escucha después de la lectura completa. Probar Silencio desde el aviso durante preparación y durante dictado: no debe abrirse una captura tardía ni un borrador.
4. Pedir «busca canción en inglés». Tras la explicación y nuevo tono, decir «Don't Stop Me Now» y luego repetir con «Sweet Child O' Mine». Revisar el título reconocido y el resultado real de Poweramp. Probar «cancel» en la captura inglesa. Si informa que falta `en-US`, instalar el paquete de reconocimiento offline del motor del teléfono antes de repetir.
5. Regresión: siguiente, pausa, reanudar, volumen y una llamada al contacto de prueba habitual; al colgar, confirmar recuperación del viaje y que Anterior abre otra orden. Validar ambos modos de activación utilizados por el usuario.

Si vuelve a faltar el tono o la escucha, copiar **Último resultado** y el reporte de Pruebas inmediatamente después. Registrar si falla antes de dictar, después de leer el mensaje o al abrir WhatsApp. `READY_CUE_STARTED` prueba que Android aceptó iniciar el tono, no que se haya oído.

## Validación local

Validación final: `testDebugUnitTest`, `assembleDebug` y `lintDebug` completaron con código de salida 0. Las 124 pruebas del proyecto pasaron sin fallos ni errores; lint terminó sin errores. XML de recursos y `git diff --check` correctos; integridad del APK de validación comprobada.

Pruebas deterministas cubren ruta tardía/transitoria/ausente, cancelación, reemplazo y callbacks atrasados, recuperación limitada por silencio, falsos eventos de voz causados por el tono, comandos de borrador y conservación de títulos en inglés. Estas pruebas no certifican el enrutamiento físico Bluetooth ni la apertura final de las apps externas.
