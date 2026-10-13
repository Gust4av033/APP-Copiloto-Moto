# UI ruta 02 · Ajustes y paleta

Base: UI ruta 01 (`4394a05`). Rama única: `feature/fase-1-intent-router`.

## Ajustes

La pestaña muestra una lista de categorías. Cada entrada abre un submenú; el botón fijo Volver a Ajustes y Atrás de Android regresan a la lista. Cada categoría conserva su desplazamiento y estado visible al volver. Abrir Ajustes desde Inicio/Viaje o pulsar de nuevo la pestaña abre la lista principal.

| Categoría | Opciones |
|---|---|
| Activación | Modo 1/2/3, solo activación manual, botones del casco, frase y sensibilidad. |
| Audio y música | Dispositivos, rutas/volúmenes y reproductor para búsquedas. |
| Burbuja y pantalla | Interruptor de burbuja, permiso, volver a mostrar y mantener pantalla encendida. |
| WhatsApp y aplicaciones | Disponibilidad, contactos, borradores y actualización de apps. |
| Navegación con Waze | Casa/trabajo/búsqueda, alcance y última solicitud. |
| Llamadas | Permisos, contacto de prueba y eliminación de ese contacto. |
| Pruebas y ayuda | Accesos a guía de comandos y herramientas de diagnóstico. |

La navegación no toca audio ni preferencias. Se conservan los bloqueos del servicio para cambios durante captura, llamada o restauración. En activación manual se explica por qué la frase y sensibilidad no se aplican. Al regresar de Comandos/Pruebas se recupera el submenú de origen.

## Paleta

- Morado: `#7136B8`, con base oscura `#351257` en la tarjeta de viaje.
- Celeste: `#79DDFF`, en botones y acentos de la tarjeta.
- Azul intenso: `#173F8E`, extremo del degradado; `#145BB7` para acentos diurnos.
- Temas claro y oscuro con superficies violetas suaves y texto de alto contraste.
- Ventana flotante e icono adaptativo usan los mismos acentos. No se incorporan imágenes ni decoraciones que tapen controles.

## Comprobación

1. Entrar en Ajustes, abrir las siete categorías y volver con el botón fijo y Atrás.
2. Desplazarse en Audio o Activación, volver a la lista y entrar otra vez: comprobar posición.
3. Desde Pruebas y ayuda abrir Comandos/Pruebas y regresar al submenú.
4. Con viaje activo comprobar bloqueos de modo, casco y configuración; navegar por los menús no debe detener ni reiniciar el viaje.
5. Revisar colores en tema claro/oscuro, texto grande, burbuja e icono.

Los previews cubren la lista en claro/oscuro/texto grande y el submenú de audio. Las combinaciones principales de texto se verificaron por luminancia relativa (mínimo 5,31:1 en los pares comprobados). Esto no sustituye la revisión física completa de accesibilidad.

Validación desde una compilación limpia: `assembleDebug`, `assembleFieldTest`, `testDebugUnitTest` y `lintDebug` correctos. 110 pruebas JVM sin fallos ni errores; lint sin errores (con advertencias). Recursos XML y diff revisados. El aspecto, retorno de submenús, texto grande y Bluetooth finales se comprueban en el teléfono.
