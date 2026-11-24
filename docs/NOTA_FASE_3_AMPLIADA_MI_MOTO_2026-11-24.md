# Prioridades adelantadas · Fase 3 ampliada

Nota de trabajo separada, 24 de noviembre de 2026. No sustituye ni renumera la
especificación V4, requisitos, arquitectura, registro de modificaciones ni fases
oficiales. El pedido del propietario autoriza adelantar estas capacidades antes
de la semiconversación y comenzar por Mi Moto.

## Base comprobada

- Se leyeron `AGENTS.md`, README, UI tormenta refinada V3, auditoría de ramas,
  RC1, bases de clima y plan de navegación propia; se consultaron los documentos
  originales de `docs/referencias`, especialmente Mi Moto, persistencia y fases.
- El checkout inicial `work` estaba limpio: sin cambios locales rastreados ni
  archivos sin seguimiento. No se descartó ni se ocultó trabajo local.
- Después de actualizar referencias, las únicas ramas remotas eran `main`
  (`64d0179`) y `feature/ui-storm-refined` (`3f213e7`). La segunda es ancestro
  de la primera y ambas tienen el mismo árbol. `64d0179` añade la integración
  de PR #22. Se eligió por esa comprobación, no por el nombre `main`.
- Rama de implementación: `feature/fase-3-ampliada-mi-moto`, desde `64d0179`.
- Se conserva el informe del propietario: casco estable según sus pruebas.
  Las pruebas físicas de esta entrega se distinguen de ese antecedente.

## Orden adelantado y avance real

| Prioridad | Entrega y aceptación | Estado de esta entrega |
| --- | --- | --- |
| 1. Mi Moto | Foto, nombre, modelo, kilometraje inicial/manual corregible, mantenimiento local | Implementado para una moto; ver validación abajo |
| 2. Viaje con mapa | Ubicación y traza GPS reales, distancia estimada, duración, velocidad y controles de la sesión existente | Pendiente. Reutilizar `AndroidLocationSource`, filtros y sesión actual; no iniciar un segundo GPS |
| 3. Clima | Actual y por horas para ubicación y destino inequívocos, fuente/hora/vigencia, error y caché explícitos | Evaluación completada; proveedor de red y pantalla pendientes |
| 4. Mis viajes | Historial persistente, selección, mapa de traza y resumen, borrado/exportación coherentes | Ya existe almacenamiento local de muestras/resúmenes; falta su pantalla y mapa. Muestras crudas se retienen 30 días |
| 5. Preparar viaje | Destino, ruta realmente disponible, clima y mantenimiento pendiente | Pendiente. Reunir estados reales; la distancia lineal no es ruta vial |

La semiconversación queda después de estas prioridades. No se agregan botones
para mapa, clima o preparación mientras no tengan respaldo funcional. Waze y
Google Maps mantienen sus opciones y las distinciones entre solicitud y efecto
confirmado.

## Mi Moto implementado

- Entrada funcional desde Inicio, pantalla secundaria con vuelta al origen y
  componentes Glass existentes. Kotlin/Compose; sin dependencia visual nueva.
- SQLite privado versionado (`motorcycles.db`, versión 1), perfil con identidad
  local y registros de mantenimiento relacionados. Sin cuenta, red ni permisos
  nuevos. Alcance inicial: **una moto**, sin atribuirle viajes antiguos.
- Nombre, marca/modelo opcional y lectura inicial del tablero en km, editable.
  Campo vacío significa desconocido; cero solo si el usuario lo ingresa. Hasta
  tres decimales, coma o punto, sin agrupadores. Se conserva como metros enteros
  para evitar errores de redondeo al guardar.
- Foto elegida con Photo Picker/fallback del sistema. Copia privada reducida
  (máximo 1280 px en su lado mayor), entrada limitada a 20 MB, orientación EXIF
  aplicada y recompresión sin metadatos de ubicación. Sustitución fallida conserva
  la foto anterior. Se puede quitar. No se retiene el permiso de la URI original.
- Mantenimiento: alta, edición y eliminación con confirmación, trabajo, fecha,
  km opcionales, notas y próxima revisión opcional por fecha/km. Valida fechas,
  límites y secuencia; no inventa diagnósticos ni recomendaciones del fabricante.
- Estado pendiente solo al alcanzar fecha o lectura manual configuradas. Sin
  odómetro manual, el vencimiento por km se declara desconocido. No hay alarmas,
  voz espontánea ni tareas programadas en esta entrega.
- Se reutiliza el índice de viajes reales finalizados para mostrar el total GPS
  en una tarjeta separada. El total puede incluir otra moto; no se suma al tablero
  ni se usa en mantenimiento. Un historial ilegible muestra error, no cero.
- E/S, decodificación y SQLite fuera del hilo de UI, en trabajador separado del
  audio. Edición bloqueada durante viaje, captura, prueba del botón o recuperación;
  abrir la pantalla solo consulta datos. Error de carga ofrece relectura y no
  habilita un perfil vacío ficticio. Ante corrupción de SQLite se conserva el
  archivo para recuperación, sin permitir que el manejador predeterminado borre
  los registros para recrear una base vacía.

### Efecto sobre otras superficies

Inicio abre Mi Moto; deja de anunciarlo como futuro. Viaje, ajustes de voz,
permisos, comandos, burbuja y notificación no necesitan acciones nuevas para
este registro manual con la moto detenida. El servicio de voz, el foco y las
políticas de activación, música, llamada, saludo y recuperación no se modifican.
Las pruebas de regresión y de hardware siguen siendo necesarias para confirmar
el comportamiento completo de la aplicación.

## Evaluación de proveedores (fuentes oficiales consultadas el 24-11-2026)

### MapLibre Native

Candidato adecuado para renderizar mapa, posición y una línea GeoJSON de GPS.
La [API Android consultada](https://maplibre.org/maplibre-native/android/api/)
se identifica como 13.7.1. El [código Android](https://github.com/maplibre/maplibre-native/blob/main/platform/android/MapLibreAndroid/build.gradle.kts)
declara minSdk 23 y Java 11, compatibles en principio con Full min 26, Play min 31
y Java 17 del proyecto. Es una comprobación documental, **no** una integración
compilada ni una validación binaria del AAR elegido.

La [guía de integración](https://maplibre.org/maplibre-native/android/examples/getting-started/)
usa `MapView`. En Compose se puede alojar mediante `AndroidView`, conectando
explícitamente ciclo de vida y liberación; reutilizar las muestras del GPS propio
en lugar de habilitar automáticamente otro motor de ubicación. La
[lista de artefactos](https://maplibre.org/maplibre-native/android/examples/artifacts/)
distingue OpenGL y Vulkan: fijar explícitamente versión/backend y verificar GPU,
ABI, alineación de páginas de 16 KB, R8, rotación y consumo con audio en el S22+.

El [SDK tiene licencia BSD de dos cláusulas](https://github.com/maplibre/maplibre-native/blob/main/LICENSE.md),
sin tarifa de licencia del motor. Estilo, teselas, fuentes, datos y alojamiento
tienen licencias/costos separados. Con OSM se requiere atribución y condiciones
ODbL; la [política de teselas públicas](https://operations.osmfoundation.org/policies/tiles/)
no ofrece SLA ni permite descarga masiva/prefetch. No elegir servidores de demo
como infraestructura de producción ni prometer mapas offline gratis. Falta
seleccionar cobertura/proveedor, cuota y presupuesto; no se activó facturación.

Mapa integrado **no entrega por sí mismo** ruta vial, instrucciones giro a giro,
tráfico, ETA vial ni recálculo. Esos alcances requieren un motor/adaptador aparte,
sus datos y validación. Waze sigue disponible para navegación externa.

### Open-Meteo

La [API de pronóstico](https://open-meteo.com/en/docs) ofrece condiciones actuales
y variables horarias por coordenadas mediante HTTPS/JSON. Es compatible en
principio con Kotlin sin SDK Android nuevo y encaja detrás de `WeatherProvider`;
el contrato existente deberá ampliarse para horas, destino, fecha de captura y
unidades. Proponer inicialmente temperatura, sensación, precipitación/probabilidad,
código WMO y viento, limitando variables y horizonte. Interpretar zona horaria
de cada ubicación; no confundir clima de origen con clima de destino.

Los [términos](https://open-meteo.com/en/terms) restringen la API gratuita a uso
no comercial, con menos de 10 000 consultas/día, 5 000/hora y 600/minuto. La
[tarifa publicada](https://open-meteo.com/en/pricing) además lista 300 000/mes;
no hay garantía de disponibilidad para la modalidad gratuita. Los límites se
aplican por aplicación/IP, no deben suponerse reiniciados para cada instalación.
Uso comercial requiere suscripción y endpoint con clave; Standard publica
1 millón de llamadas/mes. La página consultada no expuso un importe monetario
verificable para la suscripción: confirmar precio al contratar, sin asumir €0.

Los datos requieren atribución CC BY 4.0. La licencia AGPL del servidor no debe
confundirse con la licencia de datos ni con los términos del servicio alojado.
No se contrató servicio ni se guardó una clave. Antes de habilitar red, incorporar
elección expresa, límites/caché y manejo de 429, timeout y falta de conexión.
Enviar coordenadas a clima/teselas cambia la garantía actual de no sacar datos
de viaje del teléfono: requiere divulgación específica y revisión de privacidad
y declaraciones Play en esa entrega. Open-Meteo declara retención de logs que
pueden contener coordenadas durante 90 días. Mi Moto no hace esas solicitudes.

## Verificación de esta entrega

Resultado final: **build y verificaciones aprobados**, con la adaptación de
dependencia descrita debajo. No interpretar capturas JVM como pruebas físicas
del S22+ o del Y12 Pro.

| Comprobación | Resultado |
| --- | --- |
| `testFullDebugUnitTest` | 349 casos: 347 aprobados, 2 omitidos por condiciones de variante/debug, 0 fallos/errores |
| `testPlaystoreDebugUnitTest` | 349 casos: 346 aprobados, 3 omitidos por condiciones de variante/debug, 0 fallos/errores |
| Nuevas pruebas Mi Moto | 22 ejecuciones por variante; persistencia, corrección/borrado, archivo SQLite dañado conservado, separación GPS, orientación/privacidad de foto, navegación, bloqueo y CRUD desde UI |
| `assembleFullFieldTest` y `assemblePlaystoreDebug` | APK generados; firma debug válida comprobada con `apksigner` |
| Alineación APK | Ambos pasan `zipalign -c -P 16 4` |
| `lintFullDebug` / `lintPlaystoreDebug` | 0 errores; 149 / 190 advertencias y 12 sugerencias en cada variante |
| Contraste y UI existente | `SettingsContrastTest` y todas las pruebas de UI incluidas en ambas suites, sin fallos |
| `git diff --check` | Sin errores |

Total: **693 aprobados y 5 omitidos**, sin fallos ni errores. Las omisiones son
casos existentes condicionados por variante o build, no pruebas nuevas de Mi
Moto desactivadas. Los informes locales se generan en `app/build/reports` y
`app/build/test-results`. La suite conserva la regresión de activación, ciclo
de audio, música, llamadas y navegación existente.

Lint conserva advertencias fuera de esta entrega. En Mi Moto señala el uso de
`android.media.ExifInterface` y sugiere la extensión KTX de transacciones. Se
mantiene la API de plataforma disponible desde antes de minSdk 26 y la transacción
SQLite explícita; la orientación y eliminación de EXIF se prueban con JPEG real.
La prueba con fotos Samsung/HEIC y la revisión física permanecen pendientes.

SHA-256 de los APK verificados:

- `app-full-fieldTest.apk`: `a472840200bf27658e943a6b57b7261718b095a60669f1e1041b1b2ff20439c1`.
- `app-playstore-debug.apk`: `f006ad1270830c6180952e660381b24afd63169eb331d1436c543226c841d195`.

Comandos del proyecto para repetir con acceso normal a sus repositorios:

```sh
./gradlew testFullDebugUnitTest testPlaystoreDebugUnitTest \
  assembleFullFieldTest assemblePlaystoreDebug lintFullDebug lintPlaystoreDebug \
  --max-workers=2 --console=plain
```

Los tests de repositorio usan el mínimo del manifiesto de cada variante (Full
API 26, Play API 31) y API 35. La UI se ejecuta en API 35; incluye un flujo de
alta/edición/borrado de mantenimiento conectado a SQLite real.

### Particularidades del entorno de verificación

La descarga directa de JitPack sigue bloqueada (CONNECT 403); la solicitud para
añadir ese dominio quedó cancelada. Para verificar sin sustituir la voz por un
stub se clonó la **fuente oficial** de `msnilsen/openwakeword-android`, etiqueta
`0.1.2`, commit `b49f3ab14cf558ac0c2aef73a5e6c83da7a08558`, en `/tmp`.
Se compiló la biblioteca real con el toolchain de APP (AGP 8.13.2, Kotlin 2.3.10,
SDK 36 y JDK 17), publicándola en un Maven temporal. Se comprobó que **ningún
archivo de `openwakeword/src` cambió** y que los cinco modelos ONNX del AAR son
idénticos byte por byte a los originales de esa etiqueta.

SHA-256 del AAR reconstruido:
`701a3691dcf809e85706140fbd000be3a02f5ef797191f5d41ef542f7718dc2f`.
Un init script temporal enruta únicamente ese módulo al Maven local; el resto
usa Google/Maven Central y el ONNX Runtime 1.23.2 declarado por APP. Se configuró
proxy/CA del entorno y caché escribible para los JVM de pruebas, sin desactivar
TLS. No se modificaron dependencias, versiones, manifests ni build scripts de
APP para esta adaptación. No se incorporaron binarios de terceros a Git.

Estos resultados verifican una compilación con la implementación y los modelos
reales recompilados, **no la identidad binaria con el AAR publicado en JitPack**.
Repetir el comando normal con ese repositorio accesible antes de promover un
candidato de distribución; mantener pendiente la validación física del casco.

El APK `fullFieldTest` corresponde al paquete
`com.gust4av033.appcopilotomoto.pruebas`, copia Campo con firma debug y versión
existente `1.2.1-rc1-campo` (22). Es independiente del paquete principal. Una copia
Campo ya instalada puede requerir la misma firma para actualizarse; conservar
sus datos y verificarlo antes de instalar.

Capturas inspeccionadas de la pantalla Compose real en Android JVM. Los campos
«Moto de prueba»/«Modelo de prueba» y su lectura son fixtures de prueba; no se
precargan en la aplicación. También se comprobó alcanzar las acciones con fuente
200 % mediante desplazamiento.

| Vista | Oscuro | Claro |
| --- | --- | --- |
| Mi Moto, 100 % | [Captura](images/fase-3-mi-moto/mi-moto-night-100-top.png) | [Captura](images/fase-3-mi-moto/mi-moto-day-100-top.png) |
| Guardar y separación GPS, 200 % | [Captura](images/fase-3-mi-moto/mi-moto-night-200-profile.png) | [Captura](images/fase-3-mi-moto/mi-moto-day-200-profile.png) |

## Validación pendiente en S22+ con Y12 Pro

1. Conservar la instalación estable y probar una copia separada. Crear perfil,
   corregir lectura manual, elegir foto Samsung/galería/nube, cancelar selector,
   sustituir/quitar foto, cerrar forzosamente y reiniciar el teléfono; verificar
   persistencia. Probar foto vertical y denegación/error del proveedor.
2. Registrar, editar y eliminar mantenimiento. Revisar fecha inválida, próxima
   revisión por fecha/km, odómetro desconocido y recordatorio vencido. Comprobar
   que un viaje no incrementa el odómetro manual.
3. UI clara/oscura, fuente 200 %, teclado, TalkBack, navegación Atrás y fluidez.
   Abrir Mi Moto durante sesión: se puede leer, no editar ni activar micrófono.
4. Con Y12 Pro y el reproductor habitual: activación por frase y botón, órdenes
   consecutivas, dictado/confirmación/cancelación, siguiente/pausa, llamada entrante
   y saliente, restauración tras llamada y desconexión/reconexión Bluetooth.
   Verificar música reanudada solo cuando corresponde, una vez y por el casco.
5. Waze al frente y burbuja, pantalla bloqueada, iniciar/finalizar viaje con GPS
   opcional apagado/encendido y permisos revocados. Comparar total GPS finalizado
   con el índice real; confirmar que borrar historial no borra moto/mantenimientos.

No se declara estabilidad de producción ni aprobación Play por pasar un build.
