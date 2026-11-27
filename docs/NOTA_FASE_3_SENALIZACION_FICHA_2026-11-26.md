# Señalización y ficha técnica — avance separado

Base: `feature/fase-3-mapa-principal`, `fc88c37`. No se incorporan borradores ni se renumeran las fases oficiales.

## Avance 1: sentidos y nombres de calles

Flecha original incluida en la app, con borde oscuro y relleno blanco, en calles con `oneway=1` o `oneway=-1`. Se respeta la geometría orientada del proveedor; no se deduce el sentido del movimiento GPS ni del recorrido. Las flechas inversas rotan 180° y no se enderezan automáticamente al girar la cámara. Desde zoom 16, separación de 120 unidades de pantalla y colisiones activas para limitar saturación. Se excluyen senderos, vías férreas y obras. Nombres con preferencia español/nombre local, texto mayor al acercar y halo legible.

Fuente: [esquema transportation de OpenMapTiles](https://github.com/openmaptiles/openmaptiles/blob/master/layers/transportation/transportation.yaml). El estilo previo ya tenía flechas; se sustituyen sus sprites por el recurso local y se mejora su presentación. No se agregan servicios, permisos ni lectura GPS.

La capa [poi](https://github.com/openmaptiles/openmaptiles/blob/master/layers/poi/poi.yaml) contiene paradas de transporte, que **no son señales STOP**. Este proveedor no ofrece una capa completa verificada de stops, ceda el paso y semáforos. No se dibujan señales supuestas. Incorporarlas exige una fuente cartográfica específica, comprobar cobertura y antigüedad y determinar a qué aproximación de la intersección aplican. Tampoco se interpreta el estado rojo/verde de un semáforo. Las opciones del mapa explican esta cobertura; la señalización física prevalece. Waze continúa disponible; no se introducen giros hablados ni garantías de prevención de accidentes.

Validación: `assembleFullFieldTest`, `TripMapPolicyTest` y `SettingsContrastTest` aprobados. Verificación física pendiente: flechas en calles de sentido conocido, cámara Norte/Rumbo/Libre y legibilidad del mapa diurno/nocturno. Sin cambios en el servicio de audio.

## Avance 2: capacidades del catálogo y edición explícita

El usuario confirmó Ignitor 125 **año 2025 y tanque de 10 L**. Se carga automáticamente la referencia del catálogo de 10 L/5 velocidades: cilindrada 124,7 cc, tanque 10 L, 5 velocidades y peso sin carga 123 kg. Fuente: [ficha oficial Hero El Salvador de agosto de 2023](https://heromotos.com.sv/wp-content/uploads/2023/08/EL-SALVADOR_Ignitor125_agosto10_2023.pdf). La pantalla distingue la fecha de publicación de la ficha del año de la motocicleta; no atribuye una homologación específica de 2025 a ese documento. No utiliza el manual de la variante de 11 L.

La ficha guardada se abre en lectura; Editar Mi Moto habilita cambios. En una ficha nueva, las especificaciones precargadas requieren Editar especificaciones. Guardar persiste únicamente correcciones personales, sin mutar el catálogo. Restaurar devuelve los valores de referencia dentro del borrador y requiere Guardar. Descartar recupera la ficha guardada. Campos vacíos representan ausencia de dato, no cero. No hay cálculos de combustible disponible, autonomía o límites de conducción a partir de esas cifras.

Migración SQLite 1/2 → 3: añade correcciones de especificaciones; conserva nombre, kilometraje manual, foto anterior, ID de catálogo y registros de mantenimiento. Modelos escritos sin ID de catálogo siguen sin especificaciones supuestas. Aceite, presión de neumáticos y carga máxima se dejan sin valores por no haber contrastado el manual exacto de esa versión.

No se agregan permisos, proveedores de red automáticos, locuciones ni cambios de audio. Abrir la ficha oficial es una acción explícita del usuario. La burbuja y notificación permanecen sin cambios porque estos datos se muestran solo en Mi Moto y no alteran la sesión.

### Cierre y validación

Campo **1.2.1-rc3-campo**, código 24. `assembleFullFieldTest` y `assemblePlaystoreDebug` completados; firma APK y alineación de 16 KB verificadas. Selección final de pruebas `Motorcycle*Test`, `SettingsContrastTest` y `SettingsNavigationUiTest`: 64 casos, 63 aprobados, 1 omitido, sin fallos ni errores. Se verifican migraciones desde SQLite 1/2, persistencia al reabrir, conservación de mantenimiento, rechazo de valores inválidos y edición explícita sin modificar el catálogo. No se repitió la suite completa del avance anterior.

Revisión visual de capturas de pruebas JVM de Mi Moto: [oscuro 100 %](images/fase-3-senalizacion-ficha/mi-moto-night-100-specs.png), [claro 100 %](images/fase-3-senalizacion-ficha/mi-moto-day-100-specs.png), [oscuro 200 %](images/fase-3-senalizacion-ficha/mi-moto-night-200-specs.png) y [claro 200 %](images/fase-3-senalizacion-ficha/mi-moto-day-200-specs.png). Los valores de lectura usan texto con contraste normal y desplazamiento vertical; no cajas deshabilitadas atenuadas. Son capturas de fixtures de pruebas, no datos simulados dentro del producto ni evidencia del mapa nativo en dispositivo.

Pendiente en S22+ con Y12 Pro: actualización conservando ficha/mantenimiento, selección y corrección de capacidades, flechas sobre calles de sentido conocido y cámara girada, y regresión de activación, música, llamadas y recuperación del audio. No se han modificado `TripVoiceService.kt` ni `OpenWakeWordProvider.kt`. El mapa nativo requiere validación física: los endpoints cartográficos no pudieron comprobarse desde este entorno y el emulador no estuvo disponible por memoria insuficiente.
