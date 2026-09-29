# Base UI de APP Copiloto Moto

La base visual avanza en paralelo con la validación de fase 0. Inicio reúne el estado actual y la preparación previa; Viaje concentra la sesión; Ajustes reúne la configuración. Pruebas sigue disponible desde estas pantallas. Esta organización prepara el crecimiento del producto mientras se valida el flujo de audio.

## Punto de partida

- Audio y pruebas de campo: `feature/fase-0-audio-bluetooth` (incluye los cambios de 0.3).
- Interfaz: `feature/ui-base-compose`, creada desde el commit `1f01daabd6f17c0be53da3f4a94f6ce67af6c98c` de la rama de audio.
- Estado integrado: fase 0 hasta `8f80d0edbda961bc81c7a906b7a5f2378bfcf905`, incorporada a la UI por el merge `25c8d6caab8556f86f957f18dab11bc6fb56a504`. El servicio y los proveedores conservan ese flujo, con diagnóstico automático antes/durante/después.
- Acuerdo del chat 0.1: Alexa sigue como activación principal; los toques cortos del Y12 siguen disponibles para siguiente/anterior canción y la pulsación larga queda como respaldo.
- Continúa pendiente validar el volumen bajo al iniciar el viaje, la calidad de Bluetooth, activaciones y estabilidad en el S22+ con el Y12. Esta UI no cierra fase 0.

El Documento 03 propone Compose y separación de presentación/runtime. Aunque la UI completa aparece más adelante en el plan original, el acuerdo actual permite adelantar esta base para apoyar las pruebas y moldear la presentación del producto. Mi Moto, preparación e historial tienen espacios informativos marcados como próximos; sus funciones, navegación, SOS, cuentas y memoria siguen pendientes.

## Pantallas

| Pantalla | Contenido |
| --- | --- |
| Inicio | Estado real, iniciar/abrir el viaje, configuración actual, acceso a pruebas y módulos próximos. |
| Viaje | Estado real de la sesión, iniciar/detener, última orden, comparación automática de audio desplegable, guía breve y métricas. |
| Pruebas | Ruta secundaria con diagnóstico de niveles, orden corta, medición de micrófono, detector con APP visible y selección de asistente para Y12. |
| Ajustes | Frases con modelo disponible, sensibilidad, entrada de activación y configuración de llamadas existente. |

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

El estado de sesión continúa perteneciendo a `TripVoiceService`/`TripTestState`. La pantalla recibe un snapshot y callbacks; no crea proveedores. Los métodos de pruebas, audio, reconocimiento y llamadas conservan su implementación. No se añaden dependencias Gradle ni permisos.

Durante lint se encontraron tres errores ya presentes en la base: dos llamadas de API 31 dentro de `selectHeadset()` y la falta de una declaración de telefonía opcional en el manifiesto. Se documentó el requisito de API de ese método, cuyos llamadores ya verifican Android 12, y se declaró `android.hardware.telephony` como opcional. Son declaraciones de compatibilidad; no cambian la selección de ruta ni los comandos.

Pantalla seleccionada, frase, sensibilidad y entrada se restauran mediante el estado guardado de la Activity. Scroll y expansión de detalles usan estado guardado de Compose. Esto cubre recreaciones de pantalla; todavía no es una configuración persistente con DataStore. Las llamadas siguen usando el almacenamiento existente para el contacto fijo.

Los textos de estado y errores que genera el runtime siguen llegando en español desde fase 0. Su futura internacionalización corresponde a esa capa. No se muestran conectividad, GPS, batería o datos de moto simulados.

## Coordinación con el trabajo de audio

1. Los cambios visuales se concentran en `presentation/` y recursos.
2. Las correcciones de audio siguen en `feature/fase-0-audio-bluetooth`. Al incorporar nuevas señales, actualizar únicamente el puente `AppUi()` y el contrato de estado necesario.
3. Antes de probar juntos cambios nuevos de audio y UI, integrar la última rama de audio en la rama de UI y resolver explícitamente cualquier conflicto de `MainActivity`.
4. El PR de UI apunta a `feature/fase-0-audio-bluetooth`. Validar primero en el teléfono; integrar en `main` según el cierre del trabajo de fase 0.

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
