# UI ruta 01 · Fase 1

Base revisada: `722ba103` (12 de octubre de 2026). Todo continúa en `feature/fase-1-intent-router`.

## Diseño

- Colores azul petróleo/turquesa, tema diurno/nocturno, tipografía y formas comunes.
- Icono adaptativo de la moto y burbuja con los mismos colores.
- Inicio: tarjeta principal de viaje, configuración breve, accesos a Comandos/Pruebas y módulos futuros compactos.
- Viaje: estado del servicio, pedir orden en activación manual, iniciar/detener, confirmación pendiente y última orden. Audio y métricas desplegables.
- Comandos: grupos desplegables para música/volumen, modos, WhatsApp/llamadas, Waze y apps. Incluye las opciones tres/cuatro de borradores y búsqueda explícita de lugares.
- Borradores: mensaje destacado, Abrir borrador en WhatsApp y Descartar borrador; el envío continúa en WhatsApp.
- La revisión se identifica como **UI ruta 01 · Fase 1** al pie de las pantallas.

## Fluidez y carga

La revisión encontró consultas de AudioManager en el hilo principal cada segundo, lectura del rol de asistente durante composición y preparación repetida del catálogo de apps al reanudar la Activity.

Las lecturas de audio y la preparación completa de comandos ahora usan un executor de presentación independiente de las pruebas de micrófono. No se consultan roles desde el cuerpo Compose. Las pantallas componen secciones visibles mediante LazyColumn; las consultas repetidas se agrupan y el catálogo se reutiliza durante 30 segundos. Actualizar aplicaciones fuerza otra lectura cuando no hay una ya pendiente. Las respuestas de una Activity destruida se descartan. Una selección de casco cambiada durante la consulta exige otra lectura. La evidencia temporal de controles se sincroniza al ser leída desde otro hilo.

Esto elimina esos trabajos del hilo de UI. No demuestra todavía que desaparezca toda pausa en el S22+: es necesario comparar apertura fría y regreso desde Waze con el dispositivo real. No cambia el motor de voz, la confirmación de acciones ni la liberación de audio.

## Comprobación en S22+ / Y12 Pro

1. Instalar la rama de Fase 1 y confirmar el marcador al final de Inicio.
2. Cerrar APP y abrirla cinco veces. Comprobar que se puede tocar Viaje/Ajustes mientras termina de cargar el catálogo.
3. Abrir Waze y volver a APP cinco veces. Comprobar que el viaje sigue activo y que Inicio/Ajustes responden.
4. Revisar tema claro/oscuro y tamaño de texto grande; expandir Comandos y volver, comprobando posición de scroll.
5. Iniciar modo botón, pedir una orden, abrir/ocultar burbuja y detener. Comprobar restauración de música y bloqueo del botón mientras el servicio prepara audio.
6. Revisar un borrador y descartarlo. Verificar las instrucciones uno/dos/tres/cuatro. Probar Waze con el protocolo `PRUEBA_SALIDA_02_2026-10-12.md`.

Registrar por separado: tiempo hasta primera pantalla, tiempo hasta que responde un toque y tiempo hasta lecturas de Bluetooth/catálogo. No confundir carga de datos con bloqueo de controles.

## Validación

`assembleDebug`, `assembleFieldTest`, `testDebugUnitTest` y `lintDebug` completaron correctamente. 110 pruebas JVM, cero fallos/errores; lint sin errores (con advertencias). Se comprobaron recursos XML y limpieza del diff. Los previews cubren tema claro/oscuro, texto grande, recuperación de llamada, borradores y cambio de modo. La revisión visual en dispositivo, Bluetooth, micrófono y fluidez en el S22+ requieren validación física.
