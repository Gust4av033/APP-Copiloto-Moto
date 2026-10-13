# APP Copiloto Moto

Baseline integrada actual: **Fase 1 RC1**, versión `1.0.0-rc1`, en `release/fase-1-rc1`. Reúne toda la UI glass y la esfera de agua de `1d58c29`, con panel compacto y adaptación a texto/pantalla ([detalle de UI](docs/UI_BASE.md)) y el núcleo de Escucha 03 con reconocimiento automático español/inglés según capacidades del motor, aclaraciones habladas y preparación/cancelación común. [Estado y alcance](docs/FASE_1_RC1.md) · [Regresión repetible](docs/REGRESION_FASE_1_RC1.md). Es candidata a estable: la validación de los cambios nuevos en S22+/Y12 se registra por separado de las pruebas automatizadas.

Proyecto Android nativo para el copiloto de voz orientado a motociclistas. Esta rama inicia la fase 1 sobre el prototipo de fase 0: reúne las órdenes en un Action Executor, amplía las frases de música y volumen, y descubre los accesos instalados para abrir aplicaciones por voz. **Comandos** reúne la guía, una comprobación de frases sin ejecución y los accesos de apps disponibles con búsqueda. Inicio y Viaje muestran las aperturas pendientes y los estados de llamada/recuperación. El viaje y el audio Bluetooth siguen en validación con hardware real.

En Ajustes y Pruebas, **Dispositivos de audio** permite elegir el casco de comunicación o mantener Automático. Muestra salida musical prevista, ruta de voz, niveles y la última entrada medida por separado. La salida de música de otras apps se elige en Android. Los cambios y nuevas capturas quedan bloqueados durante el viaje y la restauración del audio. Ver el protocolo de comparación en `docs/PRUEBA_AUDIO_PERFILES_Y12_SKULLCANDY.md`.

La burbuja del copiloto es opcional y se habilita en Ajustes para el viaje sobre otras apps. Los modos 1 (voz), 2 (botón) y 3 (experimental) pueden cambiarse por una orden capturada. Ver `docs/BURBUJA_Y_MODOS.md` para configuración y pruebas.

El panel se minimiza con × o tocando fuera; Ocultar burbuja la retira durante el viaje. «Escribe a [nombre] en WhatsApp» permite dictar y revisar un borrador antes de abrirlo con uno/dos. El envío se realiza en WhatsApp; contestar/rechazar llamadas entrantes por voz todavía no está disponible. Lista de pruebas: `docs/PRUEBA_VIAJE_BORRADORES.md`.

La revisión **UI ruta 01 · Fase 1** consolida el diseño diurno/nocturno, agrupa los comandos de WhatsApp/Waze y mueve la preparación de audio/catálogos fuera del hilo de interfaz. Alcance y comprobación de apertura: [docs/UI_RUTA_01.md](docs/UI_RUTA_01.md).

La revisión **UI ruta 02 · Ajustes y paleta** organiza Ajustes en siete submenús y aplica la paleta morado/celeste/azul en ambos temas, tarjeta, burbuja e icono. [Detalle y comprobación](docs/UI_RUTA_02.md).

## Abrir en Android Studio

1. Clonar el repositorio con **File > New > Project from Version Control** e introducir su URL HTTPS de GitHub. También se puede usar `git clone <URL>`.
2. Abrir la carpeta raíz **APP-Copiloto-Moto**, donde está `settings.gradle.kts`; no abrir solamente `app`.
3. Dejar que Android Studio use el Gradle Wrapper del proyecto y su JDK 17. Instalar Android SDK Platform 36 si se solicita.
4. Pulsar **Sync Project with Gradle Files**. Después, seleccionar un teléfono o emulador y ejecutar `app`.

La primera sincronización requiere Internet para descargar Gradle y las dependencias. `local.properties` se genera en cada computadora y no se sube a GitHub.

## Base técnica

- Kotlin, Jetpack Compose y un único módulo Gradle `app`.
- Android Gradle Plugin 8.13.2, Gradle 8.13, Kotlin/Compose Compiler 2.3.10 y JDK 17.
- `compileSdk` y `targetSdk` 36; `minSdk` 26.
- Las versiones de bibliotecas se centralizan en `gradle/libs.versions.toml`.
- El paquete inicial es `com.gust4av033.appcopilotomoto`; cambiarlo más adelante requiere revisar `namespace` y `applicationId`.

## Alcance inicial

La Fase 0 debe validar en un teléfono real con casco: audio Bluetooth, frase de activación, STT breve, intención determinista, «siguiente canción», cancelar/silencio y métricas de aciertos, fallos y latencia. Los proveedores (wake word, STT, TTS, navegación y demás) se incorporarán tras medir el flujo mínimo. No se solicitan permisos Android antes de implementar las funciones que los necesitan.

La arquitectura prevista conserva límites entre `presentation`, `application`, `domain`, `data`, `integration` y `service` dentro de `app` cuando exista código que corresponda a cada capa. No se crean módulos Gradle vacíos. La navegación seguirá siendo agnóstica; Waze no se fija como dependencia del dominio.

## Documentación y trabajo en Git

- `docs/UI_BASE.md`: alcance de la interfaz, estructura, integración en fase 1 y comprobaciones en el teléfono.
- `docs/FASE_0.md`: objetivo, validación y orden de implementación.
- `docs/FASE_1.md`: primera entrega del núcleo de comandos y su prueba en el S22+.
- `docs/referencias/`: documentos originales del proyecto. La especificación V4, los requisitos y la arquitectura son la línea base. El registro de modificaciones aprobadas complementa los documentos base según su regla de precedencia. Las indicaciones iniciales se conservan como antecedente.
- `main` es la base estable. Todo el trabajo de fase 1, incluida la UI, se concentra en `feature/fase-1-intent-router`; probar en el teléfono antes de integrar mediante PR.
- No incluir claves, tokens, firmados, `local.properties`, grabaciones personales ni datos de viajes en Git.

### Sincronizar cambios de GitHub

En Android Studio: **Git > Pull** sobre `feature/fase-1-intent-router` o `git pull --ff-only origin feature/fase-1-intent-router` desde la terminal del proyecto. Para subir cambios propios: **Commit** y luego **Push** (o `git add . && git commit -m "..." && git push`).


## Estabilización del núcleo y botón configurable

Documento 04 aplicado: captura opcional de un control multimedia durante el viaje, métricas por etapa y extracción gradual del servicio. La recepción del botón con Poweramp/Waze/pantalla bloqueada requiere regresión física.

[Diseño y alcance](docs/ESTABILIZACION_1_0.md) · [Pruebas S22+/Y12](docs/PRUEBA_ESTABILIZACION_1_0.md)

Corrección del 04-10-2026: confirmación de apps desde el servicio de viaje con teléfono desbloqueado y permiso Mostrar sobre otras apps; lectura de borradores y dictado con plazos separados. Prueba detallada: [apps y WhatsApp](docs/CORRECCION_APPS_WHATSAPP_2026-10-04.md).

Estabilización 03 corrige la pérdida del botón dedicado con música activa. El APK independiente **APP · Prueba botón 03** se compila con `assembleFieldTest` y permite probar sin reemplazar la instalación habitual. [Configuración y pruebas](docs/CORRECCION_BOTON_MUSICA_2026-10-04.md).

Estabilización 04 distingue el permiso multimedia de la copia de prueba, abre su pantalla específica y muestra si está habilitado. El APK **APP · Prueba botón 04** actualiza la copia 03 manteniendo su identificador. [Permiso y diagnóstico](docs/PERMISO_BOTON_2026-10-04.md).


