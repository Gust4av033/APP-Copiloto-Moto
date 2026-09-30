# Fase 1 — núcleo de comandos

## Núcleo implementado

- El Intent Router interpreta la frase una sola vez y devuelve la intención junto con sus parámetros. Conserva la distinción entre «detener» (pausar música) y «detener sesión» (cerrar el viaje), y rechaza frases combinadas o volumen fuera de rango.
- El Action Executor compartido ejecuta música y volumen y resuelve la apertura de apps, tanto en la prueba visible como en la sesión de viaje. Los ajustes de volumen de la sesión siguen esperando la restauración de la ruta de audio antes de actuar.
- Se aceptan algunas formas naturales adicionales, entre ellas «pon la música en pausa», «pasa de canción», «aumenta el volumen» y «por favor, abre Spotify».
- «Abre [nombre]» consulta los accesos instalados en el perfil actual del teléfono. **Pruebas → Aplicaciones del teléfono** muestra los nombres y comandos disponibles; se actualiza al abrir APP y con el botón **Actualizar aplicaciones**. Si instalás o quitás una app, la siguiente orden vuelve a consultar Android.
- Cuando varios accesos tienen el mismo nombre, APP no elige uno por su cuenta: pide «abre [nombre] número uno/dos». La lista muestra el identificador del paquete solo en esos casos para distinguirlos. Cada orden abre el acceso concreto que muestra el lanzador, incluso si un mismo paquete ofrece varias entradas.
- Una orden «cancelar» o una orden nueva quita la apertura pendiente de la notificación. Durante el anuncio hablado de una llamada, la acción **Silencio** de la notificación detiene esa voz y cancela la llamada sin marcar.

## Abrir aplicaciones

Con APP visible, la orden abre la aplicación encontrada. En la sesión de viaje, APP deja la aplicación preparada en una acción **Abrir [nombre]** de la notificación. Android restringe abrir una pantalla desde un servicio en segundo plano; esta entrega requiere tocar esa acción. El inventario local se limita a los accesos que Android publica al lanzador en el perfil actual. Las apps sin acceso de lanzador, deshabilitadas o instaladas solo en otro perfil no aparecerán. La lista no se envía ni se guarda.

Esta acción no es navegación ni controla la aplicación abierta. La integración con Waze corresponde a Fase 2.

## Prueba en el S22+ con la moto detenida

1. Abrir **Pruebas**, pedir una orden corta y decir «por favor, pasa de canción». Verificar el cambio de pista. Repetir «pon la música en pausa» y «reanuda».
2. Decir «volumen al 40 por ciento» y comprobar el nivel de la ruta que muestra APP. Decir «volumen al 80» sin «por ciento»: no debe ajustarlo, porque excede el rango de nivel directo.
3. Abrir **Pruebas → Aplicaciones del teléfono** y revisar la lista. Elegir una app cualquiera, no necesariamente popular, y decir el comando mostrado. Confirmar que abre el acceso correcto. Probar un nombre inexistente: no debe abrir otra.
4. Repetir en la sesión de viaje con la pantalla apagada. Verificar que aparece **Abrir [nombre]** en la notificación y que solo al tocarla se abre la aplicación. Pedir otra app y luego decir «cancelar»: la acción anterior debe desaparecer.
5. Comprobar que música, volumen y detener sesión siguen funcionando. Si probás una llamada con confirmación hablada, tocá **Silencio** durante el anuncio: debe detener la voz, cancelar la llamada y dejar la sesión esperando otra activación.

La compilación y las pruebas locales verifican las reglas y el código Android. El comportamiento del botón, del casco, de la notificación y de las aplicaciones instaladas requiere esta prueba en el teléfono antes de integrar la rama. «Silencio» pronunciado durante el anuncio aún no puede reconocerse porque el micrófono de órdenes está cerrado mientras habla; la acción de notificación permite interrumpirlo en esta etapa.
