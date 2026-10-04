# Identificar copia y permiso del botón · Estabilización 04

Después del APK 03, el usuario volvió a reportar que el botón solo funcionaba una vez sin música y no con música. Al consultar el diagnóstico seleccionó **Permiso o copia distinta**. Esa respuesta no distingue entre falta de acceso de la copia de prueba y uso de la instalación habitual; no permite concluir que el nuevo mecanismo de recepción haya sido probado con su permiso correcto. La recepción física sigue sin validación.

Se encontró una ambigüedad en la configuración: ambas instalaciones mostraban el servicio como **APP · controles del reproductor**, y el botón de permiso abría la lista general de acceso a notificaciones. La revisión 04:

- En Android 11+, abre directamente el componente de permiso de la instalación desde la que se pide. Usa `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` con `EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME`. Si el fabricante no expone esa pantalla, vuelve a la lista general.
- Nombra el acceso de la copia de prueba **APP · Prueba botón 04 · controles del reproductor**.
- Muestra explícitamente si el acceso está concedido para el nombre de esta app, y permite revisarlo también cuando ya está habilitado.
- El diagnóstico informa cuando el botón está desactivado en esta instalación o falta el permiso, indicando que hay que configurarlo y reiniciar el viaje.

No se cambia el mecanismo de prioridad de la revisión 03 ni se presenta esta revisión como solución física confirmada. No se concede un permiso automáticamente. Las pruebas unitarias anteriores siguen comprobando política de pulsaciones y recuperación, sin representar pruebas en el casco.

## Instalación y comprobación

El APK completo tiene el mismo identificador `.pruebas` que 03 y versionCode 2. Se verifica que esté firmado con la misma clave de prueba que el APK 03 entregado, para actualizarlo conservando ajustes y permisos. La copia habitual de Android Studio conserva su identificador diferente.

1. Instalar la actualización y abrir **APP · Prueba botón 04**. Detener cualquier viaje activo en la app habitual.
2. En Ajustes, seleccionar **Anterior canción → Escuchar orden**. Tocar el botón de acceso multimedia; permitir el acceso de **APP · Prueba botón 04 · controles del reproductor**.
3. Volver a APP: debe mostrar **Acceso multimedia habilitado para APP · Prueba botón 04**. Detener e iniciar el viaje para crear el coordinador con ese permiso.
4. Reproducir música, esperar a que la sesión indique que está lista, y pulsar Anterior. Repetir cinco veces, dejando terminar cada orden.
5. Si falla, copiar la línea **Botón** y los contadores `button_down_received` y `button_activation` de Pruebas, antes y después de pulsar. Si no aumentan, el evento no llegó a APP; si aumentan, investigar activación/STT/recuperación. Si se informa otro destinatario, registrar el reproductor exacto. No se declara éxito sin esta evidencia.

Referencia: [Settings y pantalla específica de acceso a notificaciones](https://developer.android.com/reference/android/provider/Settings).

Validación local: compilación limpia del APK fieldTest, 81 pruebas sin fallos, lint sin errores. Se comprobaron firma idéntica a 03, integridad ZIP, identificador `.pruebas`, versionCode 2 y los nombres de app/permiso dentro del APK. No se ejecutó la prueba física del botón en este entorno.
