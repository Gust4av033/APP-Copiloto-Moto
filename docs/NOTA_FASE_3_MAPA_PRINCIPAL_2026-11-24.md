# Mapa como pantalla principal

La rama `feature/fase-3-mapa-principal` parte de `917fd94`, que reúne Mi Moto y Viaje con mapa. En una apertura normal, el mapa ocupa el centro desde la primera pantalla. Los accesos superiores abren el panel Inicio, la ficha de Mi Moto y las opciones de GPS, calles y navegador. El borde inferior reúne los controles de la sesión y el estado de voz. La pestaña Viaje conserva el resumen detallado y las acciones de confirmación. Al volver desde Mi Moto se recupera la pantalla de origen.

## Al abrir y al iniciar un viaje

La vista general incluye los contornos terrestres de Natural Earth a escala 1:110 millones, empaquetados en la app. Se pueden explorar sin permisos ni conexión. Al aceptar calles por Internet, el mapa usa las mismas teselas y condiciones de OpenStreetMap de la entrega de Viaje. El proveedor puede inferir la zona visible. La opción se puede retirar durante la sesión desde el mapa.

La Ignitor aparece sobre la primera posición aceptada por el registrador. Usa el dibujo azul existente de la app, anclado a la coordenada mediante la proyección de cámara de MapLibre. Al mover el mapa, el símbolo permanece sobre esa coordenada. Si la muestra envejece o termina el viaje, la moto pierde intensidad, el texto indica «Última posición» y se oculta la velocidad. Si se desactiva el GPS o se revoca su permiso, se retiran tanto la moto como el recorrido de la pantalla principal.

La cámara sobre la moto inicia con 42° de inclinación. El botón 2.5D / 2D cambia entre esa perspectiva y la vista cenital. Centrar recupera el seguimiento y Ver trazo ajusta la cámara al recorrido. La cartografía raster se proyecta sobre una superficie plana. Edificios con volumen y relieve requerirían datos y capas adicionales. Waze sigue disponible para guiar por carretera.

La sesión se inicia con un toque explícito. Los controles llaman a los mismos eventos del servicio que Viaje. La burbuja, notificación y atajos de voz conservan sus destinos para escuchar, confirmar o cancelar una orden del casco. Los dibujos seleccionables, colores y capacidades por modelo quedan como una futura ampliación de Mi Moto.

Datos de la vista general y condiciones de uso. [Natural Earth 5.1.2](https://github.com/nvkelso/natural-earth-vector/blob/v5.1.2/geojson/ne_110m_land.geojson) · [Dominio público](https://www.naturalearthdata.com/about/terms-of-use/). La aplicación incluye la atribución en los avisos de licencias.

## Verificación y prueba de campo

El APK Campo abre el mapa mundial al arrancar sin permiso de ubicación. La [captura de apertura](images/fase-3-mapa-principal/apertura-campo.png) proviene del emulador Android API 26. La prueba de viaje utiliza coordenadas sintéticas aisladas. Se verificaron los píxeles de la moto y del trazo en sus coordenadas proyectadas antes y después de mover la cámara a 42°. [Posición inicial](images/fase-3-mapa-principal/moto-inclinada.png) · [Después de desplazar](images/fase-3-mapa-principal/moto-tras-desplazar.png).

El primer símbolo registrado en el estilo de MapLibre se podía localizar mediante la API, aunque en pantalla solo aparecía el punto. El marcador entregado usa una vista Android con la proyección de MapLibre. La comprobación espera el cuadro dibujado completo antes de comparar las posiciones. En el emulador con renderizado por software hubo un aviso nativo de memoria durante las pruebas previas. La fluidez y el consumo prolongado requieren una salida real.

La suite Full Debug incluye navegación al perfil, inicio explícito de sesión, retirada del permiso GPS, contraste y controles. Las capturas de la disposición de controles utilizan un área de mapa de prueba identificada. [Claro 100 %](images/fase-3-mapa-principal/map-day-100.png) · [Oscuro 100 %](images/fase-3-mapa-principal/map-night-100.png) · [Claro 200 %](images/fase-3-mapa-principal/map-day-200.png) · [Oscuro 200 %](images/fase-3-mapa-principal/map-night-200.png).

APK Full Campo, firma de pruebas y alineación de 16 KiB verificadas. SHA-256 `f66486764249fa0baf5e2b38444869da5e595db3649d31fa52ffdff66e3cb5bb`. Para openWakeWord se usó la misma reconstrucción temporal desde el código oficial que en la [entrega de Viaje](NOTA_FASE_3_VIAJE_MAPA_2026-11-24.md). El proyecto conserva la dependencia declarada.

En el S22+ con Y12 Pro queda probar el primer GPS al aire libre, la legibilidad con calles y sol, el cambio de perspectiva y el retorno desde Waze. Con música sonando, comprobar la activación por casco, una llamada y la recuperación de audio. En una salida larga, revisar también batería y temperatura.
