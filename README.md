# APP Copiloto Moto

Proyecto Android nativo para el copiloto de voz orientado a motociclistas. Esta rama inicia la fase 1 sobre el prototipo de fase 0: reúne las órdenes en un Action Executor, amplía las frases de música y volumen, y descubre los accesos instalados para abrir aplicaciones por voz. **Pruebas** muestra los nombres de apps disponibles. El viaje y el audio Bluetooth siguen en validación con hardware real.

En Ajustes y Pruebas, **Dispositivos de audio** permite elegir el casco de comunicación o mantener Automático. Muestra salida musical prevista, ruta de voz, niveles y la última entrada medida por separado. La salida de música de otras apps se elige en Android. Los cambios y nuevas capturas quedan bloqueados durante el viaje y la restauración del audio. Ver el protocolo de comparación en `docs/PRUEBA_AUDIO_PERFILES_Y12_SKULLCANDY.md`.

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

- `docs/UI_BASE.md`: alcance de la interfaz, estructura, coordinación con fase 0 y comprobaciones en el teléfono.
- `docs/FASE_0.md`: objetivo, validación y orden de implementación.
- `docs/FASE_1.md`: primera entrega del núcleo de comandos y su prueba en el S22+.
- `docs/referencias/`: documentos originales del proyecto. La especificación V4, los requisitos y la arquitectura son la línea base. El registro de modificaciones aprobadas complementa los documentos base según su regla de precedencia. Las indicaciones iniciales se conservan como antecedente.
- `main` es la base estable. Para un cambio grande, crear una rama `feature/nombre`; probar en el teléfono y fusionar mediante PR. Borrar la rama temporal tras integrar.
- No incluir claves, tokens, firmados, `local.properties`, grabaciones personales ni datos de viajes en Git.

### Sincronizar cambios de GitHub

En Android Studio: **Git > Pull** o `git pull origin main` desde la terminal del proyecto. Para subir cambios propios: **Commit** y luego **Push** (o `git add . && git commit -m "..." && git push`).
