# Señalización y ficha técnica — avance separado

Base: `feature/fase-3-mapa-principal`, `fc88c37`. No se incorporan borradores ni se renumeran las fases oficiales.

## Avance 1: sentidos y nombres de calles

Flecha original incluida en la app, con borde oscuro y relleno blanco, en calles con `oneway=1` o `oneway=-1`. Se respeta la geometría orientada del proveedor; no se deduce el sentido del movimiento GPS ni del recorrido. Las flechas inversas rotan 180° y no se enderezan automáticamente al girar la cámara. Desde zoom 16, separación de 120 unidades de pantalla y colisiones activas para limitar saturación. Se excluyen senderos, vías férreas y obras. Nombres con preferencia español/nombre local, texto mayor al acercar y halo legible.

Fuente: [esquema transportation de OpenMapTiles](https://github.com/openmaptiles/openmaptiles/blob/master/layers/transportation/transportation.yaml). El estilo previo ya tenía flechas; se sustituyen sus sprites por el recurso local y se mejora su presentación. No se agregan servicios, permisos ni lectura GPS.

La capa [poi](https://github.com/openmaptiles/openmaptiles/blob/master/layers/poi/poi.yaml) contiene paradas de transporte, que **no son señales STOP**. Este proveedor no ofrece una capa completa verificada de stops, ceda el paso y semáforos. No se dibujan señales supuestas. Incorporarlas exige una fuente cartográfica específica, comprobar cobertura y antigüedad y determinar a qué aproximación de la intersección aplican. Tampoco se interpreta el estado rojo/verde de un semáforo. Las opciones del mapa explican esta cobertura; la señalización física prevalece. Waze continúa disponible; no se introducen giros hablados ni garantías de prevención de accidentes.

Validación: `assembleFullFieldTest`, `TripMapPolicyTest` y `SettingsContrastTest` aprobados. Verificación física pendiente: flechas en calles de sentido conocido, cámara Norte/Rumbo/Libre y legibilidad del mapa diurno/nocturno. Sin cambios en el servicio de audio.
