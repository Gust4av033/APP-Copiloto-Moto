> Candidato de pruebas no comerciales **1.2.1-rc1 (22)**: [preparación, verificación y puerta de Fase 3](docs/PLAY_RC1_2026-10-17.md). Play requiere Android 12+ y STT local disponible. No publicado en Google Play.

# APP Copiloto Moto

La rama por defecto `main` conserva la base estable de Fase 1 RC1 y el contexto de navegación aceptado de Fase 2. El trabajo actual continúa en `feature/ui-storm-refined`, descendiente de `feature/fase-2-integrada`. Reúne observación y lectura experimental de Waze, Google Maps seleccionable, los sabores `full`/`playstore` y [Glass UI refinada](docs/UI_TORMENTA_REFINADA_V3.md). La validación física en S22+ y casco sigue pendiente antes de integrar a `main`. [Auditoría anterior de ramas](docs/AUDITORIA_RAMAS_FASE_2_2026-10-14.md).

En [Google Maps de Fase 2](docs/FASE_2_GOOGLE_MAPS.md), Casa y Trabajo ya admiten una dirección opcional: sin ella, APP abre una búsqueda en Maps para que el conductor elija su lugar guardado con la moto detenida; con dirección, solicita la ruta directa. APP no lee los favoritos personales ni confirma una ruta externa.

## Cierre del contexto disponible de Fase 2

La rama funcional `feature/ui-storm-refined` reúne observaciones recientes de Waze y Google Maps, sesión multimedia y batería/audio del teléfono en un contexto con fuente, confianza y vigencia. La lectura de pantalla es experimental y depende del sabor `full`, del permiso y de una sesión activa. Casa y Trabajo aceptan coordenadas opcionales además de texto; con coordenadas, APP solicita la navegación externa y puede calcular distancia lineal y rumbo, sin atribuirse una ruta vial. El GPS propio es opcional, apagado por defecto, con permiso preciso y sin ubicación en segundo plano. El historial local y el detector de movimiento en sombra pertenecen a esta entrega y requieren prueba física antes de integrar a `main`.

### MOD-009 — Tráfico colaborativo y calibración personal (RESERVADA)

Estado: reservada, **no aprobada para implementar**. Se relaciona con Fase 9 (historial), Fase 11 (cuenta) y Fase 13 (tráfico); Fase 8 corresponde a Seguridad. Orden de valor: (1) calibración personal sin servidor a partir de velocidad real por tramo y hora para mejorar ETA incluso con un solo usuario; (2) detección en sombra de lentitud y paradas para medir falsos positivos sin preguntar; (3) confirmación por voz uno/dos; (4) colaboración con opt-in separado y backend anónimo. Ir lento no prueba tráfico: puede ser semáforo, tope o lluvia. La moto filtra carriles y su tráfico no equivale al de auto. No se pedirán confirmaciones en curvas ni a alta velocidad; un «no» también aporta información. Antes de cualquier colaboración, `PrivacyTrim` recorta 300 m al inicio y al final. La calibración personal cubre el arranque en frío sin comunidad.

La [base de arquitectura para navegación propia](docs/NAVEGACION_PROPIA_PLAN.md) separa la futura ruta vial de APP de las lecturas observadas de Maps/Waze y deja Navigation Connect como adaptador opcional sujeto a costo, backend y consentimiento. No activa una integración nueva en Fase 2.

La documentación de [Ajustes de usuario y desarrollador](docs/AJUSTES_USUARIO_DESARROLLO.md) y [Fase 1 RC1](docs/FASE_1_RC1.md) se conserva como historial de la base integrada; la [regresión repetible](docs/REGRESION_FASE_1_RC1.md) separa las pruebas automatizadas de las físicas.

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
- `main` es la base estable. El trabajo vigente de Fase 2 se concentra en `feature/ui-storm-refined`; validar en el teléfono antes de integrarlo.
- No incluir claves, tokens, firmados, `local.properties`, grabaciones personales ni datos de viajes en Git.

### Sincronizar cambios de GitHub

En Android Studio, usar **Git > Fetch** y elegir `feature/ui-storm-refined` para Fase 2. Después ejecutar **Git > Pull** y **Sync Project with Gradle Files**. En terminal, desde la rama ya descargada: `git fetch origin`, `git switch feature/ui-storm-refined`, `git pull --ff-only`. Si la rama aún no existe localmente: `git switch --track origin/feature/ui-storm-refined`. Conservar o respaldar cambios locales antes de cambiar de rama. La variante `fullDebug` conserva las herramientas de desarrollo; `playstoreDebug` prueba el sabor de publicación. Ambas comparten el código general, pero se compilan e instalan por separado. Para subir cambios propios: **Commit** y luego **Push**.


## Estabilización del núcleo y botón configurable

Documento 04 aplicado: captura opcional de un control multimedia durante el viaje, métricas por etapa y extracción gradual del servicio. La recepción del botón con Poweramp/Waze/pantalla bloqueada requiere regresión física.

[Diseño y alcance](docs/ESTABILIZACION_1_0.md) · [Pruebas S22+/Y12](docs/PRUEBA_ESTABILIZACION_1_0.md)

Corrección del 04-10-2026: confirmación de apps desde el servicio de viaje con teléfono desbloqueado y permiso Mostrar sobre otras apps; lectura de borradores y dictado con plazos separados. Prueba detallada: [apps y WhatsApp](docs/CORRECCION_APPS_WHATSAPP_2026-10-04.md).

Estabilización 03 corrige la pérdida del botón dedicado con música activa. El APK independiente **APP · Prueba botón 03** se compila con `assembleFieldTest` y permite probar sin reemplazar la instalación habitual. [Configuración y pruebas](docs/CORRECCION_BOTON_MUSICA_2026-10-04.md).

Estabilización 04 distingue el permiso multimedia de la copia de prueba, abre su pantalla específica y muestra si está habilitado. El APK **APP · Prueba botón 04** actualiza la copia 03 manteniendo su identificador. [Permiso y diagnóstico](docs/PERMISO_BOTON_2026-10-04.md).




### Fase 2: asistencia en segundo plano

Durante un viaje, APP puede responder qué música suena, leer la batería y despachar búsquedas de lugares a Waze o Google Maps sin iniciar una ruta automáticamente. El saludo y la despedida se configuran en Ajustes → Activación; solo se emiten por una salida Bluetooth confirmada. Los contratos de clima y lugares propios quedan preparados para Fase 7. La narración de giros es experimental y apagada; la variante Play no incluye lectura de pantalla. Ver [alcance y pruebas pendientes](docs/FASE_2_NAVEGACION_CONTEXTO.md) y [matriz Play](docs/PLAY_STORE_DECLARACIONES.md).
