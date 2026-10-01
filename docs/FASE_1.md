# Fase 1 — núcleo de comandos

Rama de trabajo: `feature/fase-1-intent-router`. Reúne comandos, UI, recuperación de llamadas y apertura emergente.

## Núcleo implementado

- El Intent Router interpreta la frase una sola vez y devuelve la intención junto con sus parámetros. Conserva la distinción entre «detener» (pausar música) y «detener sesión» (cerrar el viaje), y rechaza frases combinadas o volumen fuera de rango.
- El Action Executor compartido ejecuta música y volumen y resuelve la apertura de apps, tanto en la prueba visible como en la sesión de viaje. Los ajustes de volumen de la sesión siguen esperando la restauración de la ruta de audio antes de actuar.
- Se aceptan algunas formas naturales adicionales, entre ellas «pon la música en pausa», «pasa de canción», «aumenta el volumen» y «por favor, abre Spotify».
- «Abre [nombre]» consulta los accesos instalados en el perfil actual del teléfono. **Comandos → Aplicaciones del teléfono** muestra los nombres y comandos disponibles; se actualiza al abrir APP y con el botón **Actualizar aplicaciones**. Si instalás o quitás una app, la siguiente orden vuelve a consultar Android.
- Cuando varios accesos tienen el mismo nombre, APP no elige uno por su cuenta: pide «abre [nombre] número uno/dos». La lista muestra el identificador del paquete solo en esos casos para distinguirlos. Cada orden abre el acceso concreto que muestra el lanzador, incluso si un mismo paquete ofrece varias entradas.
- Una orden «cancelar» o una orden nueva quita la apertura pendiente de la notificación. Durante el anuncio hablado de una llamada, la acción **Silencio** de la notificación detiene esa voz y cancela la llamada sin marcar.

## Abrir aplicaciones

En la prueba corta de Pruebas, con APP visible, la orden abre la aplicación encontrada. En la sesión de viaje, APP publica un aviso emergente **Abrir [nombre]**, separado de la notificación silenciosa del viaje. Android controla su presentación: habilitá avisos emergentes del canal Abrir aplicaciones. También conserva la acción en la notificación del viaje. Inicio, Viaje y Comandos muestran la aplicación pendiente con botones Abrir y Cancelar. Descartar el aviso, cancelar o esperar 30 segundos elimina la solicitud y sus accesos. Abrir envía la solicitud a través del ejecutor y retira la tarjeta; una orden nueva reemplaza lo que estaba pendiente. Durante el viaje se anuncia la app y se abre una escucha de confirmación: después del tono, «1»/«uno» abre y «2»/«dos» cancela. Una respuesta desconocida o el silencio no abre nada. La escucha termina a los 12 segundos y el aviso conserva su vencimiento de 30 segundos. Abrir por voz usa la Activity visible y desbloqueada; si APP está detrás de otra app o el teléfono está bloqueado, se mantiene el aviso y se explica la limitación. Cancelar, descartar, vencer o abrir con un toque también cancela el anuncio/escucha correspondiente. Se conserva la pausa musical entre las dos capturas y se reanuda después de restaurar la ruta, incluido modo botón. Android restringe las aperturas desde segundo plano; ampliar el asistente del sistema queda fuera de esta entrega. El inventario local se limita a los accesos que Android publica al lanzador en el perfil actual. Las apps sin acceso de lanzador, deshabilitadas o instaladas solo en otro perfil no aparecerán. La lista no se envía ni se guarda.

Esta acción no es navegación ni controla la aplicación abierta. La integración con Waze corresponde a Fase 2.

## Llamadas y modo botón

La confirmación o cancelación mantiene el viaje activo. Durante una llamada telefónica, APP libera el micrófono y espera el fin de la llamada; después espera la devolución de audio antes de recrear el detector. Se necesita el permiso Estado del teléfono. El modo botón conserva la pausa de música entre orden y confirmación, reanuda después de recuperar la ruta y cancela una captura sin respuesta tras 15 segundos. La orden pausa conserva la música pausada.

Protocolo de regresión: `docs/PRUEBA_LLAMADAS_Y_AVISO_APPS.md`.

## Prueba en el S22+ con la moto detenida

1. Abrir **Pruebas**, pedir una orden corta y decir «por favor, pasa de canción». Verificar el cambio de pista. Repetir «pon la música en pausa» y «reanuda».
2. Decir «volumen al 40 por ciento» y comprobar el nivel de la ruta que muestra APP. Decir «volumen al 80» sin «por ciento»: no debe ajustarlo, porque excede el rango de nivel directo.
3. Abrir **Comandos → Aplicaciones del teléfono** y revisar la lista. Elegir una app cualquiera, no necesariamente popular, y decir el comando mostrado. Confirmar que abre el acceso correcto. Probar un nombre inexistente: no debe abrir otra.
4. Repetir en el viaje con APP visible: pedir una app, esperar anuncio/tono y decir «uno» para abrir; volver a APP y repetir con «dos» para cancelar. Con otra app visible o pantalla bloqueada, «uno» no debe abrirla; el aviso queda como alternativa. Pedir otra app y cancelar: la acción anterior debe desaparecer.
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

Validación previa del avance de avisos: `:app:clean :app:testDebugUnitTest :app:assembleDebug` correctos; 22 pruebas, 0 fallos y 0 errores. Verificación de avisos y Bluetooth pendiente en el S22+.


## Pantalla encendida y confirmación numerada

Ajustes → Pantalla durante el viaje ofrece Mantener pantalla encendida, activado por defecto y guardado en preferencias. Aplica `FLAG_KEEP_SCREEN_ON` solo a la ventana de APP mientras la sesión está activa. Detener el viaje libera la pantalla; cambiar de app deja actuar el apagado normal de Android. No despierta una pantalla ya apagada, no desbloquea el teléfono y no impide el apagado manual.

Prueba en S22+:

1. Elegir un tiempo corto de bloqueo en Android, iniciar viaje con APP visible y esperar más que ese tiempo: debe seguir encendida. Repetir cambiando de pestaña y girando el teléfono.
2. Desactivar la opción durante el viaje: el apagado automático debe volver. Reactivarla y detener el viaje: también debe volver. Reiniciar APP: conservar la opción.
3. En voz y botón, pedir una app y decir «uno», «dos», una frase desconocida o no responder. Verificar app correcta, cancelación, ausencia de apertura inesperada y recuperación de música/micrófono.
4. Cancelar con la tarjeta o deslizar el aviso durante el anuncio y durante la escucha. Una respuesta tardía no debe abrir nada; el modo botón debe volver a estar listo.
5. Cambiar a otra app o bloquear manualmente el teléfono durante la confirmación: «uno» no puede abrir la app; el aviso sigue disponible hasta vencer. Volver a APP permite una nueva solicitud por voz.

La búsqueda por nombre se incorpora en el avance siguiente, descrito abajo.

Validación de pantalla y confirmación numerada: `:app:clean :app:testDebugUnitTest :app:assembleDebug` correctos, 25 pruebas sin fallos ni errores; `:app:lintDebug` correcto, sin errores. Persisten advertencias del prototipo. Anuncio, reconocimiento en el casco, mantenimiento de pantalla y recuperación de audio requieren validación en el S22+.

Referencias: [Mantener la pantalla encendida](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on) y [Restricciones para abrir actividades](https://developer.android.com/guide/components/activities/secure-bal).

## Avance 01-10-2026 · canciones y continuidad del viaje

El usuario confirmó en S22+ que mantener la pantalla encendida y abrir/cancelar apps con uno/dos funciona, y que el viaje continúa después de una llamada. Reportó cierres ocasionales y activaciones por ruido muy fuerte de vehículos; no hay una medición nueva de tasa de aciertos. La búsqueda de canciones y la nueva recuperación todavía requieren prueba física.

### Recuperación de órdenes no entendidas

En el viaje se elimina el cierre tras dos órdenes no válidas. Ruido, silencio y frases desconocidas dejan el resultado visible y vuelven a esperar Alexa o el botón. Si hay más de tres activaciones en 12 segundos, se pausa el detector 8 segundos y se recupera la espera sin terminar el viaje. No se cambia el modelo ni la sensibilidad del wake word. Los cierres por pérdida del casco, permisos, fallo de captura/ruta o una orden explícita de detener siguen vigentes. La prueba de detector visible de Pruebas conserva sus límites de diagnóstico; esta modificación corresponde al servicio del viaje.

### Reproducción por nombre

- Órdenes: «pon [nombre]», «pon la canción [nombre]», «poneme [nombre]» o «reproduce [nombre]». Se conservan acentos y caracteres del título. Siguiente/anterior/pausa/reanudar/volumen tienen prioridad y no se convierten en búsqueda.
- Ajustes → Música lista los reproductores que publican una actividad estándar de búsqueda y Poweramp cuando está disponible su receptor público. La elección se guarda. Automático prioriza Poweramp; si no está, solo elige cuando hay un único compatible. Un reproductor elegido que desaparece no se reemplaza por otro.
- Poweramp usa su receptor documentado para `MEDIA_PLAY_FROM_SEARCH`, sin abrir una Activity, y puede recibir la solicitud con otra app visible o el teléfono bloqueado. Busca en la biblioteca que tenga indexada; APP no descarga canciones ni consulta su catálogo privado.
- Otros reproductores reciben la búsqueda estándar de Android desde la Activity visible y desbloqueada. Pueden abrir su interfaz; si APP está en segundo plano, se explica el requisito sin intentar abrir una pantalla desde el servicio. No se promete compatibilidad con cualquier reproductor ni con una app que no anuncie esta función.
- En modo botón, la búsqueda espera la restauración de la ruta multimedia. Una búsqueda enviada inicia su propia reproducción: no se reenvía PLAY a la pista anterior. Si el envío falla, se restaura la pausa temporal de APP.
- El resultado indica solicitud enviada, no canción encontrada/reproducción confirmada; la biblioteca y la coincidencia final dependen del reproductor. El selector solo aplica a búsquedas, los controles multimedia existentes siguen usando la sesión que administra Android.

### Prueba en teléfono

1. Abrir Poweramp, comprobar su biblioteca y anotar dos títulos exactos. Actualizar reproductores en Ajustes; seleccionar Poweramp o Automático.
2. En modo botón, pedir «pon [título]» con música reproduciéndose. Confirmar la canción exacta y la recuperación de calidad musical; repetir partiendo con música pausada.
3. Repetir con otra app visible y teléfono bloqueado. Poweramp debe recibir la búsqueda sin tocar la pantalla. En modo voz verificar canción y continuidad de la espera.
4. Pedir un título inexistente: APP solo debe informar que envió la búsqueda; anotar qué decidió Poweramp, sin asumir que encontró el título.
5. Probar «pon la siguiente», «pon la música en pausa» y «pon volumen al 40 por ciento»: conservar sus acciones específicas.
6. En un reproductor estándar que aparezca en la lista, probar con APP visible; luego en segundo plano. El segundo caso debe explicar el requisito y conservar el viaje. Quitar/deshabilitar el reproductor elegido: no cambiar a otro silenciosamente.
7. Provocar tres órdenes desconocidas, silencios o errores de STT consecutivos: el viaje no debe terminar por contador. Si aparece pausa por ráfaga, esperar recuperación y enviar una orden válida. Detener durante esa pausa: no debe reactivar la sesión.
8. Repetir llamadas por nombre y apertura de apps con uno/dos, en ambos modos, para descartar regresiones.

Antes de cerrar fase 1 quedan por validar canciones con Poweramp/otros compatibles, continuidad ante órdenes fallidas y la regresión de contactos, cancelaciones y audio en ambos modos. No se adelanta navegación en este avance.

Fuentes de integración: [API oficial de Poweramp](https://github.com/maxmpz/powerampapi/blob/master/poweramp_api_lib/src/main/java/com/maxmpz/poweramp/player/PowerampAPI.java), [búsqueda multimedia estándar de Android](https://developer.android.com/guide/components/intents-common#PlaySearch).

Validación local del avance: `:app:clean :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` completos; 35 pruebas, cero fallos/errores, APK compilado y lint sin errores. Lint registra 43 advertencias y 9 sugerencias. La prueba de canciones, ruido y Bluetooth en el teléfono queda pendiente.


## Apertura de chats por contacto e integraciones

Extensión de los accesos a apps de fase 1: se puede pedir una acción concreta soportada por un proveedor. WhatsApp y WhatsApp Business tienen adaptadores explícitos de apertura por enlace `wa.me`, con destino restringido a la edición solicitada. No se automatizan pantallas mediante accesibilidad ni se promete controlar cualquier app. Dictado, lectura y envío de mensajes siguen siendo mensajería de fase 5.

En Ajustes y Comandos, «Integraciones y acciones» muestra accesos a apps, reproductores compatibles y disponibilidad de apertura de chats por edición de WhatsApp. «Habilitar contactos para chats» solicita solo READ_CONTACTS: no exige permisos de llamada. La agenda se consulta localmente al resolver la orden; no se transmite un catálogo de contactos.

### Prueba en el teléfono

1. Con la moto detenida, instalar/actualizar la edición deseada, actualizar la lista y conceder contactos antes de iniciar el viaje. Usar un nombre único con un único número. Se recomienda guardar el número con + y código de país. Se usa el número normalizado de Android; para un número local, Android intenta validarlo con la región de la SIM. Sin región válida, se pide corregir el contacto, sin inventar un prefijo.
2. Iniciar viaje con APP visible, desbloqueada y la pantalla encendida. Decir «abre el chat de Ana en WhatsApp» o «abre WhatsApp con Ana». Para Business: «abre el chat de Ana en WhatsApp Business».
3. Escuchar el nombre, la edición y los últimos cuatro dígitos. Decir «uno» para abrir o «dos» para cancelar. Se reutilizan el token, la expiración de 30 segundos y la tarjeta/notificación de apertura pendiente. No se envía texto. La disponibilidad de un enlace no demuestra que el número tenga cuenta de WhatsApp.
4. Probar contacto inexistente, homónimos, varios números, número inválido, permiso denegado y edición no instalada. Debe mostrar el motivo y recuperar la escucha sin cerrar el viaje; no elegir arbitrariamente persona, número ni otra edición.
5. Probar cancelación, expiración y detención del viaje durante la búsqueda/confirmación: una respuesta antigua no debe abrir el chat. En segundo plano se conserva la notificación pendiente; la apertura por voz requiere que APP esté visible y desbloqueada, igual que las demás apps. No se desbloquea Android ni se garantiza mantener encendida la pantalla de WhatsApp.
6. Volver a APP y comprobar música, volumen, apertura de apps y recuperación tras llamada, tanto por voz como por botón del casco.

La comprobación breve fuera del viaje interpreta la orden de chat y remite a la sesión: no abre un contacto sin la confirmación. «Probar frase» permite verificar el nombre y proveedor detectados sin consultar contactos ni ejecutar acciones.

### Validación y próximos avances

El usuario confirmó en su teléfono que la búsqueda reproduce canciones, que se abren apps y que subir/bajar volumen produce cambios audibles por voz y botones físicos del casco. La apertura de chats de esta entrega todavía requiere la prueba anterior en su teléfono; pruebas unitarias y compilación no sustituyen esa validación.

La base de integraciones distingue acciones soportadas y disponibles por proveedor. Un siguiente incremento de mensajería sería dictar un borrador, leerlo y permitir corregir/cancelar; el envío requiere una vía compatible y una confirmación explícita. Navegación y las demás fases conservan su alcance en la documentación del proyecto.

Validación local de chats: `:app:clean :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` completos. 45 pruebas, cero fallos y errores; APK compilado; lint sin errores, con 47 advertencias y 9 sugerencias. Queda pendiente la prueba de apertura del contacto en el teléfono.
