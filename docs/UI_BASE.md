# Base UI de APP Copiloto Moto

La base visual avanza en paralelo con la validación de fase 0. El objetivo es organizar los controles que ya existen en Viaje, Pruebas y Ajustes, y permitir mejorar su presentación sin reescribir el flujo de audio.

## Punto de partida

- Audio y pruebas de campo: `feature/fase-0-3-boton-y12-sesion`.
- Interfaz: `feature/ui-base-compose`, creada desde el commit `1f01daabd6f17c0be53da3f4a94f6ce67af6c98c` de la rama de audio.
- Acuerdo del chat 0.1: Alexa sigue como activación principal; los toques cortos del Y12 siguen disponibles para siguiente/anterior canción y la pulsación larga queda como respaldo.
- Continúa pendiente validar el volumen bajo al iniciar el viaje, la calidad de Bluetooth, activaciones y estabilidad en el S22+ con el Y12. Esta UI no cierra fase 0.

El Documento 03 propone Compose y separación de presentación/runtime. Aunque la UI completa aparece más adelante en el plan original, el acuerdo actual permite adelantar esta base para apoyar las pruebas. Mi Moto, navegación, historial, SOS, cuentas y memoria no forman parte de este cambio.

## Pantallas

| Pantalla | Contenido |
| --- | --- |
| Viaje | Estado real de la sesión, iniciar/detener, última orden, guía breve y métricas. |
| Pruebas | Diagnóstico de niveles, orden corta, medición de micrófono, detector con APP visible y selección de asistente para Y12. |
| Ajustes | Frases con modelo disponible, sensibilidad, entrada de activación y configuración de llamadas existente. |

La navegación inferior siempre permite volver a Viaje. Cambiar de pestaña no inicia ni detiene audio. Atrás desde Pruebas o Ajustes vuelve a Viaje. El diagnóstico de niveles puede consultarse durante el viaje; las pruebas que abren el micrófono y los cambios de configuración quedan bloqueados cuando el runtime ya está ocupado.

## Organización del código

| Archivo o carpeta | Responsabilidad |
| --- | --- |
| `presentation/CopilotUiState.kt` | Snapshot de datos, opciones y contrato de acciones. Sin acceso a micrófono, contactos ni servicios. |
| `presentation/CopilotApp.kt` | Navegación, conservación de scroll por pantalla y previews aislados. |
| `presentation/screens/` | Contenido de Viaje, Pruebas y Ajustes. |
| `presentation/components/` | Tarjetas, botones y selectores reutilizables. |
| `presentation/theme/` | Paleta clara/oscura según el sistema. |
| `res/values/ui_strings.xml` | Textos nuevos de la interfaz, preparados para traducción. |
| `MainActivity.AppUi()` | Puente pequeño entre los datos/acciones actuales y Compose. |

El estado de sesión continúa perteneciendo a `TripVoiceService`/`TripTestState`. La pantalla recibe un snapshot y callbacks; no crea proveedores. Los métodos de pruebas, audio, reconocimiento y llamadas conservan su implementación. No se añaden dependencias Gradle ni permisos.

Durante lint se encontraron tres errores ya presentes en la base: dos llamadas de API 31 dentro de `selectHeadset()` y la falta de una declaración de telefonía opcional en el manifiesto. Se documentó el requisito de API de ese método, cuyos llamadores ya verifican Android 12, y se declaró `android.hardware.telephony` como opcional. Son declaraciones de compatibilidad; no cambian la selección de ruta ni los comandos.

Pantalla seleccionada, frase, sensibilidad y entrada se restauran mediante el estado guardado de la Activity. Scroll y expansión de detalles usan estado guardado de Compose. Esto cubre recreaciones de pantalla; todavía no es una configuración persistente con DataStore. Las llamadas siguen usando el almacenamiento existente para el contacto fijo.

Los textos de estado y errores que genera el runtime siguen llegando en español desde fase 0. Su futura internacionalización corresponde a esa capa. No se muestran conectividad, GPS, batería o datos de moto simulados.

## Coordinación con el trabajo de audio

1. Los cambios visuales se concentran en `presentation/` y recursos.
2. Las correcciones de audio siguen en la rama de fase 0.3. Al incorporar nuevas señales, actualizar únicamente el puente `AppUi()` y el contrato de estado necesario.
3. Antes de probar juntos cambios nuevos de audio y UI, integrar la última rama de audio en la rama de UI y resolver explícitamente cualquier conflicto de `MainActivity`.
4. El PR de UI apunta a la rama de fase 0.3. Validar primero en el teléfono; integrar en `main` según el cierre del trabajo de fase 0.

No se asume sincronización automática entre chats. Este archivo registra el punto de partida y las reglas para que ambos trabajos puedan retomarse desde el código publicado.

## Abrir esta rama en Android Studio

Desde la terminal en la raíz del proyecto:

```bash
git fetch origin
git switch feature/ui-base-compose
git pull --ff-only origin feature/ui-base-compose
```

Sincronizar Gradle y ejecutar `app` en el S22+. Los previews están en `CopilotApp.kt`: día, noche, sesión activa, Pruebas, Ajustes y texto grande. Sus callbacks no hacen llamadas ni abren el micrófono.

## Verificación en el teléfono

- Recorrer las tres pestañas; verificar que Viaje conserva el estado de la sesión.
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
