# UI de APP Copiloto Moto · Fase 1

Todo el trabajo de fase 1 se concentra en `feature/fase-1-intent-router`. Esta entrega integra las mejoras previas de `feature/ui-base-compose` con el núcleo vigente de llamadas, captura por botón y apertura de apps. La rama de UI queda como antecedente; las próximas mejoras continúan en fase 1.

## Pantallas

| Pantalla | Contenido |
| --- | --- |
| Inicio | Estado real del viaje, modo de activación, preparación del casco, apertura pendiente de app y accesos a configuración, comandos y pruebas. |
| Viaje | Iniciar/detener, orden manual cuando el servicio está listo, espera por llamada y recuperación, app pendiente, última orden interpretada y resultado, niveles separados de Música/Comunicación, capturas y métricas. |
| Comandos | Guía según modo, comprobación local de una frase sin ejecución, catálogo real de aplicaciones con búsqueda y bloques de 20 resultados. |
| Ajustes | Modo, frase/sensibilidad cuando corresponde, casco, permisos/contacto de llamadas y accesos a comandos y pruebas. |
| Pruebas | Dispositivos, observaciones manuales y diagnóstico copiable, comparación de volumen, orden corta, medición del micrófono, detector y asistente. |

La navegación principal sigue siendo Inicio, Viaje y Ajustes. Comandos y Pruebas recuerdan su pantalla de origen. Cambiar de pantalla conserva la sesión y no abre una captura. Durante el viaje queda disponible el acceso de regreso a Viaje. Mi Moto, Preparar viaje y Mis viajes continúan marcados como próximos: todavía requieren sus datos y funciones.

## Correspondencia con el núcleo vigente

- El modo botón no pide Alexa: explica la apertura temporal del micrófono, la restauración de música y la cancelación automática de una captura sin respuesta a los 15 segundos. La orden manual se habilita a partir de `armed`, observado como `commandReady`; queda deshabilitada mientras se procesa, llama o recupera el viaje.
- El estado de llamada y recuperación se publica explícitamente desde el servicio. Se presenta en Inicio, Viaje y el acceso permanente; no se deduce leyendo textos de diagnóstico.
- Las tarjetas de app conservan Abrir/Cancelar con el token de la solicitud visible. El servicio mantiene su validación, reemplazo por una orden nueva, vencimiento a los 30 segundos y cancelación al descartar el aviso. La UI no abre automáticamente una app desde segundo plano.
- Comandos consulta el catálogo existente. Buscar no abre apps ni graba audio. La comprobación de frases usa `MinimalIntentRouter`; interpretar una app o contacto no confirma instalación, permisos o destinatario.
- Música y Comunicación muestran índices separados de Android. La indicación SCO/LE procede del tipo de dispositivo de comunicación actual. Una ruta seleccionada no demuestra qué micrófono usa el STT ni la calidad acústica.
- La selección de casco conocida como perdida impide iniciar el viaje. La configuración y otras capturas siguen bloqueadas durante sesión, restauración y pruebas de audio.
- El diagnóstico copiable añade modelo/Android, fecha, configuración, lecturas, última orden, métricas y observaciones manuales. Las observaciones se reinician al cambiar de sesión, modo o casco.
- Se conserva la recuperación de llamadas, el manejo de música pausada y las solicitudes pendientes del núcleo de fase 1. Las lecturas de volumen mínimo se protegen para Android 26/27, donde `getStreamMinVolume` aún no existe.

## Abrir y verificar

```bash
git fetch origin
git switch feature/fase-1-intent-router
git pull --ff-only origin feature/fase-1-intent-router
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Los previews de `CopilotApp.kt` incluyen día/noche, texto grande, voz/botón, dos cascos, selección perdida, restauración, app pendiente, catálogo y llamada/recuperación. Sus callbacks no ejecutan acciones.

### Revisión en S22+ con el casco

1. Abrir Comandos desde Inicio, Viaje y Ajustes; volver al origen. Buscar una app con acentos o distinta capitalización, probar cero coincidencias y mostrar más resultados. Girar el teléfono y comprobar la conservación de búsqueda.
2. Escribir “volumen al 40 por ciento”, “por favor, pasa de canción”, “abre [app]” y una frase desconocida. Comprobar interpretación sin ejecución.
3. Iniciar modo botón: pedir una orden, comprobar que no permite enviar otra durante el procesamiento y esperar 15 segundos sin hablar. Revisar recuperación y nueva habilitación.
4. Pedir abrir una app. Revisar la misma solicitud en Inicio/Viaje/Comandos, abrir o cancelar, dejar vencer y comprobar que una solicitud antigua no afecta una nueva. Seguir también `PRUEBA_LLAMADAS_Y_AVISO_APPS.md`.
5. Confirmar una llamada: revisar “Viaje en espera · llamada”, después “Recuperando viaje” y finalmente la espera normal. Detener durante la recuperación y comprobar que no se reactiva. La acción Silencio de la notificación interrumpe el anuncio del contacto.
6. Comparar Música/Comunicación y sonido audible, copiar el diagnóstico y repetir con el otro casco después de detener. Ver `PRUEBA_AUDIO_PERFILES_Y12_SKULLCANDY.md`.
7. Revisar tema claro/oscuro y texto grande. La compilación y las pruebas locales no sustituyen la revisión visual ni la validación de llamadas, audio y avisos en el teléfono.

## Validación de esta entrega

`assembleDebug` y `testDebugUnitTest` completaron correctamente: 22 pruebas, cero fallos y cero errores. También se verificaron los recursos XML y la limpieza del diff. `lintDebug` no pudo completarse: faltan dependencias de AndroidTest y `lint-gradle:31.13.2` en la caché; la red de este entorno no permite descargarlas. Una ejecución limitada a fuentes de la app llegó a compilar Kotlin, pero quedó bloqueada por la misma falta del motor de lint. No se cambiaron dependencias ni la configuración de lint del proyecto. Quedan pendientes lint completo, revisión visual y validación en el S22+ con el casco.
