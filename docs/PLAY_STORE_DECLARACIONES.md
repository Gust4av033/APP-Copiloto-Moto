# Inventario y declaraciones Play

Fecha de auditoría local: 17 de octubre de 2026. Fuente de verdad técnica: manifiestos fusionados `playstoreRelease` y `fullRelease`, generados con `processPlaystoreReleaseMainManifest` y `processFullReleaseMainManifest`, y `DistributionTest`. La publicación y la ficha de Play Console no se pueden comprobar desde este repositorio.

Políticas consultadas el 17-10-2026: [API objetivo](https://support.google.com/googleplay/android-developer/answer/11926878), [FGS](https://support.google.com/googleplay/android-developer/answer/13392821), [Accesibilidad](https://support.google.com/googleplay/android-developer/answer/10964491), [datos y privacidad](https://support.google.com/googleplay/android-developer/answer/10144311), [canales de prueba](https://support.google.com/googleplay/android-developer/answer/9845334). La consulta fue posible en esta revisión. No acredita aprobación de Console. Se mantiene Play sin Accesibilidad, CALL_PHONE ni READ_PHONE_STATE. Ver candidato y pendientes en [PLAY_RC1_2026-10-17.md](PLAY_RC1_2026-10-17.md).

## Matriz del manifiesto fusionado

`P` = playstoreRelease; `F` = fullRelease. Los permisos ordinarios y de servicio se conceden o controlan por Android; la divulgación de APP se muestra antes de la petición cuando corresponde.

| Permiso o servicio | Variante y estado | Función y categoría a revisar en Play | Divulgación previa / denegación |
|---|---|---|---|
| `SYSTEM_ALERT_WINDOW` | P,F incluido | Burbuja sobre navegador; superposición especial | Diálogo Burbuja; sin permiso, usar APP sin burbuja |
| `RECORD_AUDIO` | P,F incluido | Órdenes y palabra de activación; micrófono | Diálogo Micrófono; sin permiso, sesión de voz no inicia |
| `BLUETOOTH_CONNECT` | P,F incluido | Ruta y botón del casco; dispositivos cercanos | Diálogo Micrófono y Ajustes de audio; sin permiso, no se garantiza casco |
| `FOREGROUND_SERVICE` | P,F incluido | Sesión de viaje visible; servicio en primer plano | Inicio visible y notificación; sin servicio, no hay sesión persistente |
| `FOREGROUND_SERVICE_MICROPHONE` | P,F incluido | Micrófono en sesión; tipo FGS | Diálogo Micrófono; sin permiso, no hay voz de viaje |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | P,F incluido | Control de audio y botón; tipo FGS | Ajustes de audio; sin acceso, controles limitados |
| `FOREGROUND_SERVICE_LOCATION` | P,F incluido | GPS opcional durante viaje; tipo FGS | Diálogo Ubicación; sin permiso, viaje sin distancia GPS propia |
| `ACCESS_FINE_LOCATION` | P,F incluido | Posición precisa y movimiento; ubicación | Diálogo Ubicación; sin permiso, GPS propio apagado |
| `ACCESS_COARSE_LOCATION` | P,F incluido | Petición conjunta Android; ubicación | Diálogo Ubicación; sin precisión, contexto GPS no disponible |
| `POST_NOTIFICATIONS` | P,F incluido | Estado persistente y avisos; notificaciones | Inicio de sesión; si se deniega, visibilidad limitada según Android |
| `READ_CONTACTS` | P,F incluido | Resolver contacto pedido; contactos | Diálogo Contactos; sin permiso, búsqueda nominal no disponible |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | P,F generado por AndroidX | Proteger receptores dinámicos internos; permiso normal propio | No requiere diálogo; no expone función al usuario |
| `CALL_PHONE` | F incluido; P candidato excluido | Llamada directa tras confirmación; teléfono | F: permiso Android; P conserva marcador `ACTION_DIAL` sin permiso |
| `READ_PHONE_STATE` | F incluido; P candidato excluido | Detectar fin de llamada y reanudar música; teléfono | F: permiso Android; P no monitoriza llamada directa |
| `TripMediaAccessService` (`BIND_NOTIFICATION_LISTENER_SERVICE`) | P,F incluido | Acceso especial a sesiones multimedia | Diálogo Notificaciones; sin acceso, controles por sesión limitados |
| `WazeNavigationObservationService` (`BIND_NOTIFICATION_LISTENER_SERVICE`) | P,F incluido | Avisos de **Waze y Google Maps** durante viaje | Diálogo Notificaciones; sin acceso, no se obtiene contexto del navegador |
| `TripVoiceService` (`microphone\|location`) | P,F incluido | Sesión en primer plano y tipos declarados | Sesión y notificación; cada función conserva guardas por permiso y ajuste |
| `WazeScreenService` (`BIND_ACCESSIBILITY_SERVICE`) | F incluido; P excluido | Lectura experimental de texto visible, solo con herramientas de desarrollo en `full` | F: control de Accesibilidad y ajuste; P no ofrece lectura de pantalla |

No se declara `ACCESS_BACKGROUND_LOCATION`, `QUERY_ALL_PACKAGES` ni lectura de mensajes personales. `BIND_*` son permisos de vinculación impuestos al servicio, no permisos `uses-permission` solicitados por APP.

## Accesibilidad en una entrega futura

No está en la primera entrega Play: la lectura de pantalla del S22+ aportó poco para maniobras y existe una API de menor alcance, la de notificaciones. Play permite usos fuera de herramientas de accesibilidad con declaración en Console, divulgación destacada y separada en APP y video de demostración; la descripción del servicio Android no reemplaza esa divulgación. Antes de reconsiderarla se necesita una función central, validada físicamente, que las notificaciones no puedan cubrir.

Borrador de declaración para revisión: «Durante un viaje activo y solo después de una habilitación separada, APP leería texto visible de navegación en Waze o Google Maps para responder consultas de voz. No pulsaría controles ni leería mensajes de otras aplicaciones. La función se detendría al terminar el viaje». El video debería mostrar habilitación, divulgación separada, lectura de una indicación verificable y desactivación. No subir video ni declarar esta función mientras no exista en Play.

## Política de privacidad

El archivo [`privacy-policy.html`](../privacy-policy.html) describe la variante Play actual: marcador para llamadas, ubicación opcional solo con viaje activo, observación de avisos de Waze/Maps y servicio de accesibilidad solo en `full`. Hay que publicar esa versión y cotejarla con Data Safety y los formularios FGS de Play Console antes de subir el AAB. Esta auditoría local no comprueba la página ya publicada.

## Borradores para Console · no enviados

**FGS microphone:** durante un viaje iniciado por el usuario, APP recibe órdenes de voz y detecta una palabra local de activación. Si se interrumpe, deja de recibir órdenes; el usuario puede detener la sesión desde APP/notificación. Video pendiente: iniciar desde APP visible, mostrar aviso y micrófono activo, emitir una orden y detener. No incluir datos personales.

**FGS location:** seguimiento GPS opcional del viaje iniciado por el usuario; posición y movimiento para contexto e historial local. Si se interrumpe se pierde continuidad del registro, sin inventar muestras. Video pendiente: activar ajuste, divulgación y permiso, iniciar viaje, mostrar seguimiento y detener. No solicita ACCESS_BACKGROUND_LOCATION.

**mediaPlayback:** retirado en RC1. Los controles remotos y el botón no justifican declarar reproducción persistente de APP.

**Data Safety:** evaluar la transferencia fuera del dispositivo, no solo los permisos. El audio STT y wake word se procesan localmente; agenda, nombre del saludo, favoritos y GPS permanecen locales. Las exportaciones y apertura de apps externas son acciones explícitas del usuario. No marcar respuestas definitivas sin revisar el AAB final y las definiciones/excepciones de Console. No hay cuentas, anuncios ni backend de APP en este candidato. La prueba interna no acredita cumplimiento para cerradas o producción.

**Ficha sugerida:** asistente de comandos de voz durante viajes, con control multimedia por casco, búsquedas en Waze/Maps y contexto disponible con procedencia. Android 12+, servicio local de reconocimiento y casco compatibles. Modelos de activación fijos. Candidato de pruebas no comerciales, sin prometer giros propios, ETA continua, llamadas directas o conversación general.
