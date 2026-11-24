# Viaje con mapa · avance de Fase 3 ampliada

Continuación del pedido adelantado, 24-11-2026. Esta nota complementa la
[entrega Mi Moto](NOTA_FASE_3_AMPLIADA_MI_MOTO_2026-11-24.md). No renumera ni
sustituye las fases, especificación, requisitos o documentos oficiales de UI.

## Base y alcance

Se revisaron `AGENTS.md`, la UI V3, la nota anterior, declaraciones Play,
política de privacidad, ramas y cambios locales. El árbol estaba limpio en
`4f406f0`, que contiene Mi Moto sobre la integración UI/RC1 `64d0179`.
El remoto conserva esa entrega como la más avanzada; `main` sigue en RC1.
Trabajo en `feature/fase-3-viaje-mapa`, desde `4f406f0`.

Prioridad 2 implementada: mapa integrado en Viaje, última posición GPS válida,
trazo local, distancia estimada, duración y velocidad. Los controles existentes
inician/detienen la misma sesión y mantienen la orden manual según modo y estado.
Mi Moto conserva su kilometraje manual. Clima, pantalla Mis viajes, Preparar
viaje y semiconversación siguen pendientes, en ese orden adelantado.

## Uso y estados reales

- En Viaje o Privacidad, activar el GPS con la sesión detenida y conceder
  ubicación precisa. Iniciar la sesión como antes. Abrir Viaje por sí solo
  no inicia GPS, voz ni servicio.
- Para calles, habilitar **Calles por Internet (OpenStreetMap)** y aceptar la
  divulgación separada con la sesión detenida. La preferencia persiste y está
  apagada inicialmente. Se puede retirar durante el viaje; el registro GPS y
  la sesión de voz siguen funcionando.
- Sin esa elección, MapLibre muestra el trazo propio sobre un fondo simple,
  etiquetado como **sin calles**. No es un mapa vial offline precargado.
- Sin primera muestra válida no se crea un mapa centrado en una ciudad o
  coordenada ficticia y la distancia es desconocida. El tiempo comienza con
  el ID de la sesión y usa reloj monotónico, incluyendo paradas.
- Posición y velocidad llevan fuente GPS, captura, confianza y vigencia. La
  posición envejecida se etiqueta como última observada y cambia su indicador;
  la velocidad se oculta después de cinco segundos, sin precisión de velocidad
  suficiente o al finalizar. Dato ausente no se convierte en velocidad cero.
- La distancia proviene exactamente de `TripRepository.currentDistanceMeters`,
  con sus filtros existentes. El mapa solo recibe muestras que ese repositorio
  aceptó. No hay segundo motor de GPS ni cálculo distinto de kilometraje.
- El trazo enlaza únicamente avances aceptados por el registrador. Se divide
  al faltar muestras, tras deriva parada y en el antimeridiano; no rellena
  huecos con una carretera imaginada. Limita/simplifica geometría en memoria
  (4096 puntos, 128 segmentos) sin cambiar la distancia del historial.
- Android mock locations y replay se identifican como simulados y quedan fuera
  del registro real. Las fixtures de pruebas no pasan por el historial.
- Al detener, queda un resumen temporal hasta otra sesión o muerte del proceso.
  Las muestras y resumen persistentes siguen en el historial local existente,
  exportable desde Privacidad. La selección y mapa de viajes antiguos son la
  prioridad Mis viajes, todavía pendiente; muestras crudas mantienen 30 días.
- Centrar vuelve a la última posición; Ver trazo ajusta la cámara a lo registrado.
  Mover el mapa interrumpe el seguimiento hasta volver a centrar. No hay cámara
  animada continua. GPS impreciso/revocado, falta de calles o fallo del renderer
  muestran su estado sin detener la voz.

## MapLibre y cartografía

Se incorpora `org.maplibre.gl:android-sdk-opengl:13.7.1` desde Maven Central,
fijando OpenGL ES explícitamente. Es un motor Android nativo, alojado en Compose
mediante `AndroidView` y `TextureView`, sin un segundo componente de ubicación.
El [artefacto y backends](https://maplibre.org/maplibre-native/android/examples/artifacts/)
documentan el backend OpenGL como la opción de mayor trayectoria. El AAR declara
minSdk 23, compatible con Full 26 y Play 31. La integración conserva Kotlin,
Compose, AGP/JDK y la paleta Glass; el mapa no desenfoca controles.

El motor no tiene tarifa de licencia BSD; las licencias y avisos del SDK/tag se
incluyen sin conexión en Acerca de. La cartografía usa el proveedor estándar
OpenStreetMap para visualización interactiva, sin clave, cuenta ni contratación.
No se activan facturación, tráfico ni servidor propio. La
[política del proveedor](https://operations.osmfoundation.org/policies/tiles/)
exige atribución visible, identificación y caché; es best effort sin SLA. No
se promete capacidad gratuita ilimitada ni disponibilidad de producción.

La atribución enlazada permanece **sobre el mapa**, no escondida en un menú ni
debajo de contenido desplazable. Se fija un User-Agent de APP con versión y
enlace de contacto al repositorio. El transporte acepta solo GET HTTPS al host
exacto y patrón de teselas en zoom 0–19, sin query, fragmento ni redirecciones.
La caché HTTP privada (64 MiB) respeta Cache-Control, Expires y validadores del
servidor; se retiran directivas de bypass. Prefetch del SDK y delta de zoom están
desactivados. No hay descarga de zonas ni consultas automatizadas de cobertura.
Las pruebas automáticas no descargan teselas del servicio público.

## Privacidad, permisos y Play

La elección de calles permite enviar IP y números de teselas, de los que el
proveedor puede inferir el área vista. El trazo completo, historial, perfil de
moto, contactos y audio no se envían al proveedor. Ver la
[privacidad de OSMF](https://osmfoundation.org/wiki/Privacy_Policy).

El interceptor comprueba consentimiento y visibilidad antes de cualquier consulta,
también de caché. Retirar elección, ocultar el mapa o pasar a otra app cancela
las solicitudes de su cliente exclusivo. El mapa se pausa/destruye con su
composición y ciclo de vida. No usa el cliente ni el foco de audio del copiloto.

Se revisó `PLAY_STORE_DECLARACIONES.md` antes de modificar permisos. La nueva
matriz se registra aquí, conservando el documento histórico:

| Cambio | Full / Play | Motivo |
| --- | --- | --- |
| `INTERNET` | Ambas | Imágenes opcionales del mapa por HTTPS |
| `ACCESS_NETWORK_STATE` | Ambas | Conectividad del SDK; no concede ubicación |
| `ACCESS_WIFI_STATE` | Retirado del manifiesto de la dependencia | No se necesitan datos Wi-Fi |
| Ubicación precisa/aproximada y FGS location | Los permisos existentes | Misma fuente GPS en la sesión explícita |
| Ubicación en segundo plano / accesibilidad Play | No añadidas | El alcance continúa igual |

Se añade una **adenda puntual** a `privacy-policy.html` y al resumen en inglés
por el nuevo flujo opcional de datos. No se reescriben sus secciones ni se
renumera documentación. El archivo del repositorio no acredita que la política
publicada esté actualizada ni que Data Safety/Console estén aprobados. Antes de
distribuir en Play, cotejar el AAB, la política publicada y esa declaración.

## Casco, Waze y superficies

El servicio solo publica inicio, muestras aceptadas y fin del registro hacia
estado de presentación. No se alteran foco, captura, activación, conversación,
música, llamadas, saludo/despedida ni recuperación. La fuente GPS ahora vuelve
a comprobar permiso/ajuste antes de entregar muestras y tolera revocación al parar.

Viaje conserva estado, modo y acciones; el encabezado se compacta para dar
espacio al mapa. Las confirmaciones de app y la cancelación de escucha aislada
quedan antes del mapa para mantener accesibles las acciones de voz. Una muestra
GPS entregada después del cierre se descarta antes de actualizar el contexto.
Privacidad contiene la elección de calles. Inicio, burbuja,
notificación y comandos mantienen su sesión y evidencia existentes: dibujar
cartografía no inicia una acción de voz ni requiere un control nuevo en ellos.
La duración en pantalla se refresca por segundo solo con Viaje al frente.

Abrir Waze/Maps despacha la apertura del navegador elegido, sin atribuirse una
ruta confirmada. Al pasar a ese navegador, APP conserva sesión y GPS y pausa el
mapa integrado. **MapLibre no añade navegación giro a giro, ruta vial, tráfico,
ETA vial ni recálculo**. La ruta observada del navegador sigue separada del trazo.

## Validación

Comprobación final sobre el código entregado, sin cambios de implementación
durante esa ejecución:

| Comprobación | Resultado |
| --- | --- |
| `testFullDebugUnitTest` | 365 casos: 363 aprobados, 2 omitidos, 0 fallos |
| `testPlaystoreDebugUnitTest` | 365 casos: 362 aprobados, 3 omitidos, 0 fallos |
| `assembleFullFieldTest` / `assemblePlaystoreDebug` | APK de prueba generados y firma v2 debug verificada |
| `assemblePlaystoreRelease` | R8 y recursos optimizados; APK **sin firma de distribución** |
| `lintFullDebug` / `lintPlaystoreDebug` | 0 errores; 153 / 195 advertencias y 12 sugerencias en cada variante |
| Manifiestos fusionados Full/Play Release | Generados y contrastados; Play sin Accesibilidad, llamadas directas ni estado telefónico |
| `zipalign -c -P 16 4` | Correcto en los tres APK generados |
| Prueba nativa MapLibre en Android API 26 x86_64 | Trazo dibujado y verificado por píxeles; prefetch apagado, fuente de calles ausente |

Total JVM: **725 aprobados, 5 omitidos**, sin fallos. Las omisiones corresponden
a escenarios excluidos por variante o build type: pruebas de lectura de pantalla
Full, avisos Play y rechazo en una Activity Release. No se contabilizan como
pruebas físicas. Se incluyen las regresiones de Mi Moto, audio, distribución,
`SettingsContrastTest` y UI, más 16 casos de sesión, política HTTP y controles
del mapa por variante. Lint conserva deuda de recursos sin uso, recomendaciones
KTX, compatibilidad y otros avisos; entre los nuevos hay dos textos de atribución
sin uso y una sugerencia KTX. No se presenta como lint libre de advertencias.

La prueba Android usa la implementación nativa de producción dentro de una
actividad privada, no exportada y exclusiva de `fieldTest`, con datos sintéticos
identificados. No abre GPS/voz, no escribe historial ni solicita teselas. Detectó
que `WHEN_DIRTY` no es una API admitida por el renderer TextureView del SDK;
se retiró esa llamada y se verificó el dibujo después de corregirla. El backend
TextureView conserva su mecanismo de render por solicitud y límite de 30 FPS.
Los segmentos ELF de `libmaplibre.so` arm64-v8a y x86_64 tienen alineación
`0x4000` (16 KiB). Esto no sustituye medir GPU, batería y fluidez en el S22+.

Evidencias visuales revisadas:

- [Mapa nativo, fixture sin calles](images/fase-3-viaje-mapa/native-map.png):
  snapshot real del renderer, con tres puntos sintéticos; no es un recorrido real.
- UI de métricas en [oscuro 100 %](images/fase-3-viaje-mapa/trip-night-100.png),
  [claro 100 %](images/fase-3-viaje-mapa/trip-day-100.png),
  [oscuro 200 %](images/fase-3-viaje-mapa/trip-night-200.png) y
  [claro 200 %](images/fase-3-viaje-mapa/trip-day-200.png). Son escenarios JVM
  con métricas sintéticas; el renderer sustituto se rotula como prueba cuando
  se desplaza hasta él. Estas capturas muestran métricas, no cartografía real.

La captura de pantalla completa del emulador quedó cubierta por un diálogo
«System UI isn't responding» del entorno sin aceleración; se excluyó de las
evidencias publicadas. El snapshot nativo del mapa sí se pudo validar. No se
verificó en este entorno la descarga de calles ni un recorrido GPS real.

Comando final (JDK 17, SDK 36, un worker y compilador Kotlin en proceso):

```sh
./gradlew testFullDebugUnitTest testPlaystoreDebugUnitTest \
  assembleFullFieldTest assemblePlaystoreDebug assemblePlaystoreRelease \
  lintFullDebug lintPlaystoreDebug \
  processFullReleaseMainManifest processPlaystoreReleaseMainManifest
```

La adaptación temporal de openWakeWord desde fuente oficial 0.1.2 es la misma
documentada en Mi Moto: no se sustituyen modelos ni lógica por stubs, ni se
modifica su dependencia declarada. Por el bloqueo de JitPack, el comando local
usó además `-I /tmp/copiloto-validation.gradle`, que resuelve exclusivamente esa
dependencia contra la reconstrucción temporal. Su fuente/modelos se conservan;
no se afirma identidad binaria con el AAR de JitPack. Ver la
[procedencia y limitación de la compilación anterior](NOTA_FASE_3_AMPLIADA_MI_MOTO_2026-11-24.md).
MapLibre es el AAR oficial de Maven Central.

APK Campo: `app/build/outputs/apk/full/fieldTest/app-full-fieldTest.apk`, paquete
`com.gust4av033.appcopilotomoto.pruebas`, versión `1.2.1-rc1-campo` (22).
SHA-256: `813774f5b992a455f0d123d9b229fab16169484f46048af6c88bcf4be0ff92c0`.
Certificado debug SHA-256:
`e2eab04539d7c2ef36585e071dea9a693e5da3e04fc07d61b67b74010f1a5a87`.
No es una firma de producción ni un paquete publicado en Play.

## S22+ / Y12 Pro pendiente

- GPS al aire libre: primer fix, precisión, detenciones, huecos/túnel, permiso
  revocado y GPS desactivado; comparar distancia con el índice/exportación real.
- Habilitar/rechazar/retirar calles, modo avión, datos móviles y caché; comprobar
  atribución legible, brillo nocturno, reintento y ausencia de calles como estado
  explícito. El proveedor raster usa cartografía estándar, no un estilo nocturno
  vial específico.
- Centrar, pan/zoom, ajustar trazo, giro del teléfono, volver desde Waze, bloquear
  pantalla y lectura al 200 % / TalkBack. Revisar batería y temperatura en viaje
  prolongado con GPU, GPS, música y voz simultáneos.
- Repetir activación por frase/botón Y12 Pro, órdenes consecutivas, pausa/siguiente,
  dictado y cancelación, llamada entrante/saliente, recuperación de música y
  desconexión/reconexión Bluetooth. El mapa no acredita por sí solo esa regresión.

El APK Campo usa `.pruebas`: conservar la instalación estable y comprobar la firma
de cualquier copia Campo existente antes de actualizar. No se publica en Play.
