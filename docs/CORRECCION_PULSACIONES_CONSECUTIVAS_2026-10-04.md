# Pulsaciones consecutivas · Estabilización 05

El usuario informa que la primera pulsación de Anterior ya abre una orden al iniciar viaje, incluso con música activa, pero las siguientes dejan de activar la escucha. Se permite publicar directamente en `feature/fase-1-intent-router`.

## Fallo reproducido

`TripButtonPolicy` guardaba los últimos 16 identificadores `deviceId:keyCode:downTime`, sin retirarlos al recibir UP. Con diez pares DOWN/UP de Anterior y `downTime=0`, el código anterior devuelve una activación y nueve descartes. También falla la segunda pulsación completa si se repite un timestamp positivo. Las dos pruebas nuevas fallan contra la clase anterior y pasan con la corrección.

No se trata solamente de un evento artificial: [AVRCP en AOSP](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/c55eb6ca1009267198d679835ecbcbdcc4d11621/android/app/src/com/android/bluetooth/avrcp/AvrcpTargetService.java) construye las teclas con `KeyEvent(action, code)`. Ese [constructor de KeyEvent](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/KeyEvent.java) asigna cero a downTime y eventTime. Esto fundamenta la regresión, aunque todavía no se dispone de la secuencia real recibida desde el Y12 Pro en el teléfono del usuario.

## Corrección

Se recuerda solamente la pulsación que sigue presionada por dispositivo/tecla. UP termina el ciclo y permite el siguiente DOWN aunque su timestamp sea cero o se repita. Las entregas duplicadas mientras sigue presionado y los eventos de repetición se consumen. Un timestamp positivo más reciente permite reconocer otra pulsación si se perdió UP; un UP atrasado con timestamp válido no libera la más reciente.

Si no llega UP ni un nuevo timestamp válido, un nuevo DOWN sin repetición puede iniciar otro ciclo después de dos segundos sin eventos de esa tecla, usando el reloj local de recepción. Las repeticiones refrescan ese reloj y nunca activan por sí mismas. Esta recuperación evita un bloqueo permanente durante el cambio a voz; con timestamps cero y UP ausente no es posible distinguir una entrega duplicada muy tardía de una nueva pulsación física.

Una pulsación recibida mientras APP está ocupada se descarta; no se deja una orden pendiente. No se vacía el filtro al comenzar o terminar una captura, para que volver a la espera no convierta una tecla aún presionada en otra orden.

Los contadores de recepción y activación se actualizan solamente para la tecla reservada. El informe persistente añade **Última pulsación**, con decisión, disponibilidad, código, dispositivo, downTime y eventTime. La línea **Botón** conserva por separado el diagnóstico del destinatario multimedia. Así se puede distinguir falta de entrega, descarte por estado ocupado y rechazo de duplicados. No se almacena voz ni contenido de mensajes en ese registro.

## Verificación en la laptop y el casco

1. Actualizar `feature/fase-1-intent-router` y ejecutar la aplicación desde Android Studio. La interfaz debe mostrar **Estabilización 05 · pulsaciones consecutivas del botón**. La variante opcional `fieldTest` se identifica como **APP · Prueba botón 05**; no se necesita para ejecutar la aplicación habitual.
2. Seleccionar **Anterior canción → Escuchar orden**, mantener habilitado el acceso multimedia de esa instalación y usar Modo 2 · Botón. Reproducir música antes de iniciar viaje.
3. Esperar a que APP indique que espera el botón. Pulsar Anterior, esperar el tono y decir una orden. Repetir diez veces, esperando el regreso a la espera entre órdenes. Repetir empezando sin música y reproduciéndola después.
4. Confirmar que Anterior abre una sola captura por pulsación y no retrocede la pista; Siguiente/Pausa conservan su función. Probar también tras una orden sin voz o cancelada y con la pantalla apagada.
5. Si vuelve a fallar, copiar el informe con **Botón**, **Última pulsación**, `button_down_received` y `button_activation`. La última pulsación debe cambiar con cada DOWN entregado de la tecla reservada, aunque downTime y eventTime permanezcan en cero.

La recepción y el comportamiento con el casco real siguen pendientes de esta prueba física. El fallo del filtro sí se reprodujo y corrigió en código.

## Validación local

`testDebugUnitTest assembleDebug lintDebug`: 87 pruebas correctas, ninguna omitida, compilación debug correcta y lint sin errores (55 advertencias y 9 sugerencias). La compilación de las clases modificadas se hizo desde limpio. Las dos regresiones de timestamps repetidos fallan contra la política anterior; las 11 pruebas de política pasan con la nueva. Se cubren además UP perdido, repetición mientras la tecla sigue presionada, UP atrasado y pulsación recibida mientras APP está ocupada. La prueba de propiedad del botón ahora recorre diez órdenes con timestamps Bluetooth en cero.
