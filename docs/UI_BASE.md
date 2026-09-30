# Base UI de APP Copiloto Moto

La base visual avanza en paralelo con la validación de fase 0. Inicio reúne el estado actual y la preparación previa; Viaje concentra la sesión; Ajustes reúne la configuración. Pruebas sigue disponible desde estas pantallas. Esta organización prepara el crecimiento del producto mientras se valida el flujo de audio.

## Punto de partida

- Núcleo actual: `feature/fase-1-intent-router`, basado en fase 0. Audio previo y pruebas de campo: `feature/fase-0-audio-bluetooth`.
- Interfaz: `feature/ui-base-compose`, creada desde el commit `1f01daabd6f17c0be53da3f4a94f6ce67af6c98c` de la rama de audio.
- La primera tarjeta de dispositivos partió de `eae37dcc9bbc917f5d92b383330e932ebcbce128`. La actualización UI audio 02 parte de `e6419d5c88872ccaa27e163d274fd1eb2df25145`, como se detalla abajo.
- Acuerdo del chat 0.1: Alexa sigue como activación principal; los toques cortos del Y12 siguen disponibles para siguiente/anterior canción y la pulsación larga queda como respaldo.
- Continúa pendiente validar el volumen bajo al iniciar el viaje, la calidad de Bluetooth, activaciones y estabilidad en el S22+ con el Y12. Esta UI no cierra fase 0.

El Documento 03 propone Compose y separación de presentación/runtime. Aunque la UI completa aparece más adelante en el plan original, el acuerdo actual permite adelantar esta base para apoyar las pruebas y moldear la presentación del producto. Mi Moto, preparación e historial tienen espacios informativos marcados como próximos; sus funciones, navegación, SOS, cuentas y memoria siguen pendientes.

## Actualización UI comandos 01 — fase 1

La entrega anterior UI audio 02 partió de fase 0. Esta actualización integra realmente `feature/fase-1-intent-router` en `f307b9d9c9088128871412ed52fdf4f579330d56`, conservando las mejoras publicadas en la UI como `7dea2c6`. La cabecera ahora muestra **Fase 1 · Núcleo de comandos · UI comandos 01**. No se infiere la fase a partir del número del PR.

- Se incluyen el router con parámetros, `MediaCommandExecutor`, `AppLaunchAdapter`, las consultas de lanzador del manifiesto y las pruebas de fase 1. La sesión mantiene las rutas y la recuperación de audio existentes.
- Inicio muestra el alcance disponible y accesos a la guía y comprobación de frases. Viaje incorpora las formas naturales de música y la guía de abrir apps.
- Pruebas añade **Comprobar una frase**: interpreta texto mediante el mismo `MinimalIntentRouter.route` que la voz y muestra la acción y sus parámetros. No ejecuta órdenes ni comprueba instalación, permisos o destinatarios. La frase escrita se conserva al rotar o volver a la pantalla.
- La orden real interpretada se muestra en Viaje y en la prueba de voz visible. Es una proyección del `RoutedCommand` que ya produjo el runtime; no se vuelve a interpretar la transcripción para dibujar el resultado.
- Inicio y Viaje muestran el nombre del acceso de app preparado por el servicio. El usuario lo abre desde la notificación del viaje, tal como implementa fase 1; no se promete abrir una pantalla automáticamente desde segundo plano. Al detener/iniciar sesión o sustituir la app preparada, la indicación sigue el mismo valor del servicio.
- `CommandUi.kt` traduce las intenciones y parámetros a recursos de presentación. La observación de la orden y la app preparada no cambia sus condiciones de ejecución ni la confirmación de llamadas.
- El PR de esta actualización apunta a `feature/fase-1-intent-router`. El trabajo de núcleo continúa en esa rama y el de presentación en `feature/ui-base-compose`.

### Prueba específica de fase 1 en S22+

1. Confirmar **UI comandos 01**. En Pruebas escribir `por favor, pasa de canción`, `detener`, `detener sesión`, `volumen al 40 por ciento`, `volumen a 6`, `volumen al 80` y `abre Spotify`. Comprobar acción y parámetros; `volumen al 80` debe quedar sin orden admitida. Escribir no debe cambiar música ni abrir apps.
2. Usar la prueba de orden corta para `pon la música en pausa`, `reanudar` y `abre [nombre exacto de una app instalada]`. Comprobar la orden interpretada y el resultado real.
3. Iniciar viaje en el modo elegido y pedir `abre [nombre exacto]`. Ver el acceso en Inicio/Viaje y en la notificación; abrirlo desde esa notificación. La sesión debe seguir disponible según el flujo de fase 1. Detener debe quitar la indicación del acceso preparado.
4. Repetir música, volumen, cancelar, silencio y detener sesión con ambos modos de activación. Verificar botón y notificación con pantalla bloqueada; la UI conserva la disponibilidad y restauración de audio anteriores.
5. Revisar texto grande y temas claro/oscuro, incluyendo frase escrita, parámetros y nombres largos de app. Las llamadas se conservan con su protocolo de confirmación; esta entrega no modifica el destinatario ni envía mensajes.

Verificación local: `assembleDebug`, `lintDebug` y `testDebugUnitTest` completados. Once pruebas existentes aprobadas (seis de identidad de casco, dos de volumen y tres del router). Lint: cero errores, 29 advertencias y ocho sugerencias. Revisión visual, apertura de apps, notificación y Bluetooth pendientes en el S22+. La comprobación por texto interpreta la frase y no acredita ejecución ni aciertos de reconocimiento por voz.

## Historial: actualización UI audio 02 — 30 septiembre 2026

Base publicada: `e6419d5c88872ccaa27e163d274fd1eb2df25145`, compartida por audio y UI antes de esta entrega. Incluye modo botón con espera de ruta Bluetooth hasta tres segundos, recuperación de música tras fallos y volumen por nivel/porcentaje. La modificación del audio sigue perteneciendo a 0.1: en voz desde el casco se ajusta Comunicación; en botón se ajusta Música al restaurar la ruta. Esta UI no asegura que Comunicación sea audible en el S22+.

- Inicio y Viaje muestran el modo elegido. Botón no pide decir Alexa, y explica que la captura se abre temporalmente para la orden. Ajustes muestra primero el modo, con una explicación por opción; frase y sensibilidad aparecen para los modos de voz.
- El estado de restauración se presenta explícitamente. Una selección de casco perdida impide iniciar desde Inicio/Viaje, manteniendo disponible detener una sesión activa.
- El botón manual de Viaje solo está disponible cuando el servicio está preparado. `TripTestState.commandReady` observa el mismo valor de `armed`; no modifica las decisiones de la ruta, los tiempos ni el procesamiento de órdenes.
- Música y Comunicación se muestran por separado. La guía de volumen y los pasos de prueba dependen del modo. En botón, Comunicación 15/15 no se interpreta como fallo de Música. Se conserva la distinción entre índice Android, sonido observado y solicitud enviada.
- Las indicaciones de SCO/LE usan un valor estructurado obtenido de la ruta actual, en lugar de buscar texto dentro de una captura anterior. Antes/Durante/Después quedan identificados como capturas; Durante corresponde al regreso a la espera.
- Los perfiles y las observaciones manuales se despliegan a pedido para reducir texto permanente. En Pruebas se puede indicar cambio audible y recuperación musical, y copiar diagnóstico, última orden, métricas y observaciones al portapapeles. Las respuestas empiezan sin comprobar y se reinician al iniciar otra sesión o cambiar modo/casco. No hay envío externo ni grabación de audio.
- La cabecera muestra `UI audio 02` para comprobar qué interfaz se instaló. Se añaden previews para modo botón, noche, texto grande y restauración.

### Verificación específica en S22+

1. Confirmar `UI audio 02` en la cabecera. En Ajustes elegir Botón; Inicio no debe indicar decir Alexa. Al volver a Voz deben reaparecer frase y sensibilidad.
2. Iniciar viaje en Botón. Mientras se prepara/restaura la ruta, Pedir una orden queda deshabilitado. Al estar listo permite una orden; durante esa orden vuelve a bloquearse. Detener permanece disponible.
3. Probar desde el casco con pantalla apagada/bloqueada y desde Viaje/notificación. Probar `volumen a 6`, `volumen al 40 por ciento`, siguiente/anterior, cancelar y silencio. Verificar Música y sonido al recuperar la ruta; no inferir falla porque Comunicación permanezca 15/15.
4. En Pruebas registrar las dos observaciones y copiar el diagnóstico. Pegar en un editor y comprobar niveles, configuración actual, capturas, última medición y métricas. Rotar/cambiar de pantalla: conservar observaciones; iniciar otra sesión o cambiar modo/casco: reiniciarlas.
5. Detener y esperar la restauración antes de cambiar casco. Una selección desaparecida debe impedir iniciar hasta corregirla. Comprobar que ambos modos mantienen la lógica de audio de 0.1.
6. Revisar temas claro/oscuro y fuente grande, incluidos los selectores, niveles y botones. La revisión visual y Bluetooth requieren el teléfono; preparar previews o compilar no equivale a haberlos validado físicamente.

Verificación local de esta actualización: APK generado con `assembleDebug`, ocho pruebas existentes aprobadas (seis de identidad de casco y dos de interpretación de volumen) y `lintDebug` sin errores. Se corrigieron tres llamadas a `getStreamMinVolume` procedentes de la base: se usa la API desde Android 9 y un mínimo compatible para Android 8. La ruta de cálculo del S22+ se conserva. La compilación se ejecutó desde cero y se repitieron las tareas después de los ajustes finales. No se ha ejecutado la revisión visual en el teléfono ni una prueba Bluetooth nueva en esta entrega.

## Pantallas

| Pantalla | Contenido |
| --- | --- |
| Inicio | Estado real, iniciar/abrir el viaje, configuración actual, acceso a pruebas y módulos próximos. |
| Viaje | Estado real de la sesión, iniciar/detener, última orden, comparación automática de audio desplegable, guía breve y métricas. |
| Pruebas | Dispositivos de audio, protocolo de comparación de volumen/cascos, diagnóstico de niveles, orden corta, medición de micrófono, detector con APP visible y selección de asistente para Y12. |
| Ajustes | Frases con modelo disponible, sensibilidad, entrada de activación, selección de casco y configuración de llamadas existente. |

La navegación inferior contiene Inicio, Viaje y Ajustes. Cambiar de pantalla no inicia ni detiene audio. Pruebas recuerda si se abrió desde Inicio, Viaje o Ajustes y vuelve a esa pantalla; Atrás desde las pantallas principales vuelve a Inicio. Durante una sesión aparece un acceso permanente para volver a Viaje desde otras pantallas. El diagnóstico de niveles puede consultarse durante el viaje; las pruebas que abren el micrófono y los cambios de configuración quedan bloqueados cuando el runtime ya está ocupado.

## Preparación visual de próximos módulos

| Módulo previsto | Espacio preparado | Dependencias pendientes |
| --- | --- | --- |
| Mi Moto | Ficha descriptiva en Inicio y entrada en el catálogo visual. | Modelo de motocicleta, almacenamiento y reglas de kilometraje/mantenimiento. |
| Preparar viaje | Ficha descriptiva en Inicio y entrada en el catálogo visual. | Destino, proveedores de navegación, contexto, clima y autonomía. |
| Mis viajes | Ficha descriptiva en Inicio y entrada en el catálogo visual. | Registro real de viajes, repositorio y cálculo de resúmenes. |

Estas fichas son informativas y no ejecutan acciones. El catálogo `UpcomingModules.kt` define títulos, iconos y descripciones; no representa disponibilidad ni activa servicios. Cuando un módulo tenga datos y acciones reales, su pantalla podrá entrar como ruta propia y reutilizar los componentes de esta base. La configuración actual del copiloto permanece en Ajustes; voz, personalidad, seguridad y memoria podrán incorporarse por su alcance aprobado.

No se muestran kilómetros, ETA, nivel de combustible, clima, recorridos ni contactos de emergencia de ejemplo en la aplicación. Los previews continúan usando ejemplos aislados para el diseño.

## Organización del código

| Archivo o carpeta | Responsabilidad |
| --- | --- |
| `presentation/CopilotUiState.kt` | Snapshot de datos, opciones y contrato de acciones. Sin acceso a micrófono, contactos ni servicios. |
| `presentation/CopilotApp.kt` | Navegación, conservación de scroll por pantalla y previews aislados. |
| `presentation/UpcomingModules.kt` | Catálogo informativo de módulos previstos, separado del estado de capacidades reales. |
| `presentation/screens/` | Contenido de Inicio, Viaje, Pruebas y Ajustes. |
| `presentation/components/` | Tarjetas, botones y selectores reutilizables. |
| `presentation/theme/` | Paleta clara/oscura según el sistema. |
| `res/values/ui_strings.xml` | Textos nuevos de la interfaz, preparados para traducción. |
| `MainActivity.AppUi()` | Puente pequeño entre los datos/acciones actuales y Compose. |

El estado de sesión continúa perteneciendo a `TripVoiceService`/`TripTestState`. La pantalla recibe un snapshot y callbacks; no crea proveedores. La selección manual se añade al selector Bluetooth compartido; la medición y el monitor del viaje rechazan una selección explícita desaparecida. No se añaden permisos ni dependencias de ejecución. JUnit se añade solo para comprobar la identidad de la selección en pruebas locales.

Durante lint se encontraron tres errores ya presentes en la base: dos llamadas de API 31 dentro de `selectHeadset()` y la falta de una declaración de telefonía opcional en el manifiesto. Se documentó el requisito de API de ese método, cuyos llamadores ya verifican Android 12, y se declaró `android.hardware.telephony` como opcional. Son declaraciones de compatibilidad; no cambian la selección de ruta ni los comandos.

Pantalla seleccionada, frase, sensibilidad y entrada se restauran mediante el estado guardado de la Activity. Scroll y expansión de detalles usan estado guardado de Compose. Esto cubre recreaciones de pantalla; todavía no es una configuración persistente con DataStore. Las llamadas siguen usando el almacenamiento existente para el contacto fijo.

Los textos de estado y errores que genera el runtime siguen llegando en español desde fase 0. Su futura internacionalización corresponde a esa capa. No se muestran conectividad, GPS, batería o datos de moto simulados.

## Coordinación con el trabajo de audio

1. Los cambios visuales se concentran en `presentation/` y recursos.
2. El núcleo actual se desarrolla en `feature/fase-1-intent-router`; fase 0 conserva la base de audio anterior. Al incorporar nuevas señales, actualizar el puente `AppUi()` y el contrato de estado necesario.
3. Antes de probar nuevos cambios de núcleo y UI, integrar fase 1 en la rama visual y resolver explícitamente los conflictos, especialmente en `MainActivity` y `TripVoiceService`.
4. El PR actual de UI apunta a `feature/fase-1-intent-router`. Validar en el teléfono antes de integrarlo; la publicación de UI no acredita por sí sola el cierre de una fase.

No se asume sincronización automática entre chats. Este archivo registra el punto de partida y las reglas para que ambos trabajos puedan retomarse desde el código publicado.

## Abrir esta rama en Android Studio

Desde la terminal en la raíz del proyecto:

```bash
git fetch origin
git switch feature/ui-base-compose
git pull --ff-only origin feature/ui-base-compose
```

Sincronizar Gradle y ejecutar `app` en el S22+. Los previews están en `CopilotApp.kt`: Inicio, Viaje, día, noche, sesión activa, Pruebas, Ajustes y texto grande. Sus callbacks no hacen llamadas ni abren el micrófono.

## Verificación en el teléfono

- Recorrer Inicio, Viaje y Ajustes; verificar que Viaje conserva el estado de la sesión.
- Abrir Pruebas desde cada pantalla y volver: respetar la pantalla de origen.
- Durante una sesión, comprobar el acceso visible de regreso a Viaje en Inicio, Pruebas y Ajustes.
- Abrir la comparación de audio en Viaje: usar las mismas lecturas automáticas de Pruebas, antes/durante/después.
- Revisar las fichas de módulos próximos: son informativas y no muestran datos de un viaje o motocicleta ficticios.
- Abrir detalles de métricas, cambiar de pestaña y volver: conservar expansión y scroll.
- Cambiar frase/sensibilidad/entrada con la sesión detenida y girar el teléfono: conservar las selecciones.
- Iniciar una sesión: mostrar estado real, permitir detener desde Viaje y bloquear cambios de configuración.
- Ir a Pruebas durante la sesión: consultar niveles y comprobar que las otras pruebas no pueden abrir otro micrófono.
- Cancelar una orden manual y detener la prueba de activación: conservar sus controles existentes.
- Probar tema claro/oscuro y tamaño grande de texto sin recortes en botones y opciones.
- Comprobar Alexa, toques cortos del Y12, pulsación de respaldo y volumen antes/después de iniciar sesión. La validación de Bluetooth requiere hardware real.

Compilación y análisis local:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

La primera entrega de esta base pasó `assembleDebug` y `lintDebug`, con cero errores de lint. Persisten advertencias del prototipo, entre ellas compatibilidad de las bibliotecas nativas ONNX con páginas de 16 KB y actualizaciones de dependencias disponibles. No se actualizaron motores ni librerías en esta entrega. La revisión visual en el S22+ y las pruebas de Bluetooth siguen pendientes.

La entrega sincronizada con Inicio y los espacios de próximos módulos también pasó `clean`, `assembleDebug` y `lintDebug`: cero errores, 22 advertencias y 8 sugerencias del prototipo. Se comprobó que `TripVoiceService` coincide con la fase 0 vigente y que los métodos de voz/audio/llamadas de la Activity se conservaron. La nueva navegación requiere revisión en el teléfono.

## Tarjeta de dispositivos sobre el avance de 0.1

- Ajustes y Pruebas muestran los cascos que Android ofrece como dispositivos de comunicación, con nombre, perfil SCO/LE e ID temporal. Bluetooth conectado para música no implica que exista un micrófono disponible.
- Automático conserva la asociación con la salida multimedia de 0.1 y evita escoger arbitrariamente ante ambigüedad. Una selección explícita usa un casco concreto en el viaje, en el detector visible y en la medición. Elegirlo no abre una captura ni cambia el modo de audio por sí mismo.
- La preferencia se comparte en memoria entre Activity y servicio, sobrevive a recreaciones de la Activity y vuelve a Automático si Android termina el proceso. No se guardan IDs temporales permanentemente. Si desaparece o cambia la identidad del dispositivo, se pide volver a elegir; no se cambia al otro casco.
- Los selectores y el acceso a controles de Android se bloquean durante sesión, restauración del audio, escucha, detector o medición. Los callbacks también comprueban el bloqueo. Actualizar dispositivos solo consulta el sistema.
- La Activity consulta pasivamente rutas y niveles cada segundo mientras está visible; detiene la actualización en `onPause`. Al volver de Android se actualiza la lectura. No abre micrófonos.
- Se separan salida de música **prevista** por Android, ruta de comunicación actual, casco solicitado y entrada real de la última medición. Esta última no se presenta como entrada del STT o de Alexa; el viaje mantiene su comprobación de 0.1. Al cambiar de casco se borra la medición anterior.
- Desde `e6419d5`, las órdenes de voz desde el casco modifican Comunicación y las del modo botón modifican Música tras restaurar la ruta. La tarjeta muestra ambos niveles sin prometer ganancia acústica. Pruebas distingue «cambia nivel, no sonido» de «no cambia el nivel».
- Para elegir dónde suena otra app, usar **Salida multimedia** del panel rápido del Galaxy. El botón abre el panel público de volumen, con alternativa a Ajustes de sonido; no usa una API oculta para controlar la salida de otra app.
- Una ruta de comunicación selecciona entrada y salida asociadas. La combinación «micrófono Y12 / música Skullcandy en A2DP» no se garantiza: observar la ruta durante el viaje. La orden corta fuera del viaje sigue usando la entrada que decide Android; la medición y el detector sirven para comprobar el casco explícito.

Fuentes de API: [AudioManager](https://developer.android.com/reference/android/media/AudioManager) y [Settings.Panel](https://developer.android.com/reference/android/provider/Settings.Panel).

### Comprobación de esta entrega en S22+

1. Detener toda sesión/prueba. Abrir Ajustes → Dispositivos de audio y conceder Bluetooth si falta.
2. Conectar Y12 Pro y Skullcandy. Elegir Y12 y ejecutar la medición de cinco segundos desde Pruebas. Anotar entrada real y advertencias.
3. Iniciar el viaje, verificar el casco de comunicación y el estado de Alexa. Cambiar de pantalla: los selectores deben quedar bloqueados.
4. Probar bajar/subir desde un nivel medio; comprobar números y variación audible por separado.
5. Detener el viaje y verificar restauración de música. Cambiar la salida multimedia de Android, seleccionar Skullcandy y repetir con la misma canción.
6. Desconectar el casco elegido dejando el otro conectado: mostrar selección perdida y evitar iniciar en otro micrófono. Durante el viaje se conserva el cierre por pérdida de ruta.
7. Volver a Automático y comprobar el comportamiento previo de asociación/ambigüedad. Girar el teléfono: conservar selección si el proceso continúa vivo.
8. Revisar texto grande y tema claro/oscuro. Los previews incluyen dos cascos, sesión activa y selección desconectada; no ejecutan audio.

Las pruebas locales de identidad cubren selección explícita con dos cascos, desconexión sin sustitución, reutilización de ID por otra dirección/perfil/nombre y retorno a Automático. La calidad acústica, las rutas reales y la revisión visual requieren S22+ y hardware conectado.

Verificación de la tarjeta: `clean :app:assembleDebug :app:lintDebug :app:testDebugUnitTest` completó correctamente. Seis pruebas aprobadas; lint: cero errores, 23 advertencias y 8 sugerencias. Se corrigió el requisito de API de la lectura de direcciones Bluetooth, incluida una comprobación procedente de 0.1. No se actualizan motores de voz. Las advertencias del prototipo y la recomendación de centralizar la dependencia de pruebas siguen registradas por lint. Revisión visual y validación Bluetooth pendientes en el teléfono.
