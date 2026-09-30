# Fase 1 — núcleo de comandos

Rama de trabajo: `feature/fase-1-intent-router`. Reúne comandos, UI, recuperación de llamadas y apertura emergente.

## Núcleo implementado

- El Intent Router interpreta la frase una sola vez y devuelve la intención junto con sus parámetros. Conserva la distinción entre «detener» (pausar música) y «detener sesión» (cerrar el viaje), y rechaza frases combinadas o volumen fuera de rango.
- El Action Executor compartido ejecuta música y volumen y resuelve la apertura de apps, tanto en la prueba visible como en la sesión de viaje. Los ajustes de volumen de la sesión siguen esperando la restauración de la ruta de audio antes de actuar.
- Se aceptan algunas formas naturales adicionales, entre ellas «pon la música en pausa», «pasa de canción», «aumenta el volumen» y «por favor, abre Spotify».
- «Abre [nombre]» consulta los accesos instalados en el perfil actual del teléfono. **Pruebas → Aplicaciones del teléfono** muestra los nombres y comandos disponibles; se actualiza al abrir APP y con el botón **Actualizar aplicaciones**. Si instalás o quitás una app, la siguiente orden vuelve a consultar Android.
- Cuando varios accesos tienen el mismo nombre, APP no elige uno por su cuenta: pide «abre [nombre] número uno/dos». La lista muestra el identificador del paquete solo en esos casos para distinguirlos. Cada orden abre el acceso concreto que muestra el lanzador, incluso si un mismo paquete ofrece varias entradas.
- Una orden «cancelar» o una orden nueva quita la apertura pendiente de la notificación. Durante el anuncio hablado de una llamada, la acción **Silencio** de la notificación detiene esa voz y cancela la llamada sin marcar.

## Abrir aplicaciones

Con APP visible, la orden abre la aplicación encontrada. En la sesión de viaje, APP publica un aviso emergente **Abrir [nombre]**, separado de la notificación silenciosa del viaje. Android controla su presentación: habilitá avisos emergentes del canal Abrir aplicaciones. También conserva la acción en la notificación del viaje. Viaje muestra la aplicación pendiente con botones Abrir y Cancelar. Descartar el aviso, cancelar o esperar 30 segundos elimina la solicitud y sus accesos. Abrir envía la solicitud a través del ejecutor y retira la tarjeta; una orden nueva reemplaza lo que estaba pendiente. Android restringe abrir una pantalla desde un servicio en segundo plano; esta entrega requiere tocar esa acción. El inventario local se limita a los accesos que Android publica al lanzador en el perfil actual. Las apps sin acceso de lanzador, deshabilitadas o instaladas solo en otro perfil no aparecerán. La lista no se envía ni se guarda.

Esta acción no es navegación ni controla la aplicación abierta. La integración con Waze corresponde a Fase 2.

## Llamadas y modo botón

La confirmación o cancelación mantiene el viaje activo. Durante una llamada telefónica, APP libera el micrófono y espera el fin de la llamada; después espera la devolución de audio antes de recrear el detector. Se necesita el permiso Estado del teléfono. El modo botón conserva la pausa de música entre orden y confirmación, reanuda después de recuperar la ruta y cancela una captura sin respuesta tras 15 segundos. La orden pausa conserva la música pausada.

Protocolo de regresión: `docs/PRUEBA_LLAMADAS_Y_AVISO_APPS.md`.

## Prueba en el S22+ con la moto detenida

1. Abrir **Pruebas**, pedir una orden corta y decir «por favor, pasa de canción». Verificar el cambio de pista. Repetir «pon la música en pausa» y «reanuda».
2. Decir «volumen al 40 por ciento» y comprobar el nivel de la ruta que muestra APP. Decir «volumen al 80» sin «por ciento»: no debe ajustarlo, porque excede el rango de nivel directo.
3. Abrir **Pruebas → Aplicaciones del teléfono** y revisar la lista. Elegir una app cualquiera, no necesariamente popular, y decir el comando mostrado. Confirmar que abre el acceso correcto. Probar un nombre inexistente: no debe abrir otra.
4. Repetir en la sesión de viaje con la pantalla apagada. Verificar que aparece **Abrir [nombre]** en la notificación y que solo al tocarla se abre la aplicación. Pedir otra app y luego decir «cancelar»: la acción anterior debe desaparecer.
5. Comprobar que música, volumen y detener sesión siguen funcionando. Si probás una llamada con confirmación hablada, tocá **Silencio** durante el anuncio: debe detener la voz, cancelar la llamada y dejar la sesión esperando otra activación.

La compilación y las pruebas locales verifican las reglas y el código Android. El comportamiento del botón, del casco, de la notificación y de las aplicaciones instaladas requiere esta prueba en el teléfono para validar el comportamiento físico. «Silencio» pronunciado durante el anuncio aún no puede reconocerse porque el micrófono de órdenes está cerrado mientras habla; la acción de notificación permite interrumpirlo en esta etapa.


## Regresión de solicitudes de apps

1. Pedir una app y abrirla desde el aviso: verificar la app correcta, desaparición de la solicitud y continuidad del viaje. Repetir usando Abrir en la tarjeta de Viaje.
2. Pedir una app y cancelar desde el aviso, la tarjeta o por voz: ambos accesos deben desaparecer sin detener la sesión.
3. Descartar el aviso deslizándolo: la tarjeta y el acceso de la notificación del viaje deben desaparecer.
4. Esperar 30 segundos: la solicitud vence; pedir música inmediatamente antes del vencimiento para verificar que su temporizador no interrumpe el STT.
5. Pedir una segunda app mientras la primera está pendiente: un aviso o callback de la primera no puede cancelar ni abrir la segunda.
6. Detener viaje con una apertura pendiente: no debe quedar un acceso válido para abrirla.

Una solicitud de apertura enviada no confirma que otra app haya mostrado su pantalla correctamente.

Validación del avance: `:app:clean :app:testDebugUnitTest :app:assembleDebug` correctos; 22 pruebas, 0 fallos y 0 errores. Verificación de avisos y Bluetooth pendiente en el S22+.
