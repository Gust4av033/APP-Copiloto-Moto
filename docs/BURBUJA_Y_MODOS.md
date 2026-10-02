# Burbuja opcional y cambio de modos · Fase 1

Implementación en `feature/fase-1-intent-router`, junto con la UI y el núcleo de fase 1. Corresponde al overlay opcional de V4 §6.2 y RF-137, adelantado por decisión del usuario. Conserva voz o botón como forma de activación.

## Comportamiento

- Ajustes → Burbuja del copiloto: desactivada por defecto. El permiso “Mostrar sobre otras aplicaciones” se solicita al habilitarla, no al instalar ni al iniciar cualquier viaje. Si se deniega, el viaje sigue disponible mediante casco, APP y notificación.
- Se muestra con una sesión activa, APP en segundo plano, permiso concedido y teléfono desbloqueado. Al entrar en APP, bloquear o terminar el viaje se retira. No mantiene la pantalla encendida ni desbloquea el teléfono.
- La burbuja tiene un área táctil de 56 dp y se puede arrastrar por su encabezado. Al soltar se acomoda al borde próximo. Guarda borde y altura relativa; limita de nuevo su posición al girar o abrir el panel. El panel admite desplazamiento para texto grande o pantallas bajas.
- El panel contiene Pedir una orden, Abrir APP y Ocultar burbuja. × o tocar fuera minimizan el panel sin ocultar la burbuja. Ocultar permanece fuera del área desplazable y retira la ventana durante este viaje. Pedir una orden reutiliza la captura manual del servicio; se bloquea durante procesamiento, dictado, confirmaciones, llamadas y cambios de modo. Abrir APP muestra Viaje sin iniciar otra captura.
- Ocultar conserva la preferencia: aparece en el próximo viaje. Ajustes permite volver a mostrarla antes de terminar el actual; luego salir de APP para verla.
- Los indicadores derivan de fases explícitas: espera, preparación, escucha de orden, procesamiento, hablando, confirmación y cambio de modo. También presentan llamada y recuperación. Mostrar o mover la ventana no inicia un detector, micrófono ni servicio adicional.
- La ventana usa `TYPE_APPLICATION_OVERLAY`; permanece debajo de elementos críticos de Android. Un fallo de superposición se presenta como error visual y no debe detener el núcleo de audio. La posición evita salir del espacio disponible; el usuario elige un lugar que no tape indicaciones del navegador.

## Tres modos

| Orden | Resultado |
| --- | --- |
| “Activa modo 1”, “activa modo uno”, “activa modo voz” | Frase de activación desde el casco; STT breve después de activarla. SCO puede reducir la calidad de música durante la espera. |
| “Activa modo 2”, “activa modo dos”, “activa modo botón” | Micrófono cerrado en espera; orden temporal desde casco, APP, burbuja o notificación. |
| “Activa modo 3”, “activa modo tres”, “activa modo experimental” | Prueba: micrófono del teléfono para la frase y casco para la orden. Continúa siendo experimental y necesita validación detenido. |
| “Abre asistente”, “abre el asistente”, “abre copiloto” | Muestra APP en Viaje sin comenzar otra captura. Desde segundo plano necesita la autorización de superposición; requiere teléfono desbloqueado. Si Android impide la apertura, queda disponible el acceso de la notificación. |

En modo 1 o 3: decir la frase elegida, esperar el tono/reconocedor y pedir el cambio. En modo 2: activar primero una captura con el casco, la burbuja o la notificación y decir el cambio. Decir solamente “Alexa” mientras el micrófono está cerrado no activa APP. Si el STT incluye “Alexa, activa modo 1” o “Hey Jarvis, activa modo 2” en una captura ya iniciada, se admite esa forma también.

El cambio conserva sesión y métricas. Invalida callbacks anteriores, cierra el proveedor previo y espera la ruta multimedia normal antes de crear el nuevo. No deja dos capturas activas. Requiere micrófono/Bluetooth autorizados, Android con selección de comunicación y casco resoluble; no solicita permisos ni cambia de casco automáticamente desde voz. Solo guarda el nuevo modo al quedar listo. Se bloquean elecciones simultáneas y cambios durante llamadas/confirmaciones. Detener invalida la transición. Si Android no libera audio o el nuevo detector no puede verificar su entrada, se termina la sesión con el motivo y se limpia el audio; la preferencia anterior se conserva.

La selección de modo de Ajustes puede cambiarse durante un viaje cuando está listo; frase, casco, reproductor y contactos continúan bloqueados. Fuera del viaje, la comprobación escrita y la prueba corta muestran la intención; no cambian una sesión inexistente.

## Validación local · 2 de octubre de 2026

Compilación limpia y APK debug completados. Pasaron 52 pruebas unitarias, incluidas las órdenes de modos y límites de posición. `lintDebug` terminó sin errores, con advertencias. Estos resultados no validan la presentación ni las rutas de audio en hardware: quedan pendientes las comprobaciones siguientes.

## Prueba en S22+ con el casco

1. Instalación/configuración sin burbuja: iniciar viaje y pasar a Waze. No debe aparecer ventana ni solicitar superposición. Casco y notificación deben conservar su funcionamiento.
2. Habilitar desde Ajustes y denegar permiso: mantener el viaje funcional, mostrar permiso pendiente y permitir reintentar. Conceder después; volver a Waze y comprobar la burbuja.
3. Arrastrar a ambos bordes y cerca del extremo inferior. Abrir/cerrar panel, girar el teléfono y usar texto grande: mantener controles accesibles. Verificar que los toques fuera del área de la ventana lleguen al navegador.
4. Durante espera del modo 2, mostrar/mover/abrir panel: mantener micrófono cerrado y calidad musical. Pedir una orden: bloquear repetición hasta restaurar la música; probar música/volumen y captura sin hablar.
5. Pedir “activa modo 1” desde modo 2. Esperar preparación y comprobar modo visible y entrada del casco. Activar por frase y pedir “activa modo 2”: cerrar detector continuo y recuperar música/espera por botón. Repetir varias veces sin reiniciar métricas.
6. Probar 2→3→2 detenido. En 3 comprobar explícitamente entrada real del teléfono para la frase y casco para la orden; no asumir funcionamiento si Android no confirma esas rutas.
7. Pedir modo 4 o una frase compuesta: no cambiar, reproducir una búsqueda accidental ni marcar. Solicitar el modo actual: conservarlo. Probar una selección adicional durante cambio: ignorarla mientras está bloqueado.
8. Detener desde la notificación inmediatamente después de pedir un cambio: no reaparecer burbuja ni detector por callbacks tardíos. Desconectar casco durante cambio y comprobar error/restauración; no adoptar otro casco.
9. Probar llamada y apertura de chat/app existentes: mostrar hablando/confirmación/llamada/recuperación; no permitir otra captura desde burbuja. Conservar sus confirmaciones y vencimientos.
10. Tocar Abrir APP y pedir “abre asistente” tras una activación: mostrar Viaje sin otra escucha. Bloquear teléfono: retirar ventana; no desbloquearlo por voz. Conservar el control del casco/notificación que ya funciona con pantalla apagada.
11. Ocultar desde panel: viaje activo, burbuja ausente. Volver a mostrar desde Ajustes, después salir de APP. Detener/iniciar: respetar preferencia guardada. Desactivar: no mostrar en el siguiente viaje.
12. Quitar el permiso de superposición durante una sesión: retirar la ventana y conservar audio. Revisar claro/oscuro, orientación y texto grande en hardware real.

Referencias Android: [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY), [permiso de superposición](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context)) y [apertura de actividades](https://developer.android.com/guide/components/activities/secure-bal).
