# Vidrio azul tormenta e Inicio deslizable

La versión visual vigente es [tormenta medianoche v2](UI_TORMENTA_MEDIANOCHE_V2.md). El material y las pruebas siguientes documentan la entrega anterior.

## Pantalla encendida con burbuja · Fase 2 Screen 2

Por solicitud del usuario, la ventana visible de la burbuja lleva FLAG_KEEP_SCREEN_ON: minimizada o con panel abierto evita el apagado por inactividad sobre Waze/otras apps. Ocultar/desactivar la burbuja, revocar superposición o detener el viaje retira la ventana y libera esa protección. Mientras APP esté al frente, el mismo comportamiento se conserva si la burbuja está habilitada y no oculta; sin burbuja se respeta la preferencia de pantalla de APP. No se fuerza encendido/desbloqueo ni se impide el botón físico de bloqueo. No se añaden permisos o cambios de audio. Se conserva material, dimensiones y acciones.

Se actualizan las explicaciones en Apariencia y Viaje. [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_KEEP_SCREEN_ON) define la bandera para ventanas visibles; el [sistema de ventanas de Android](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/wm/DisplayContent.java) aplica la protección a las superficies visibles con esa bandera. No se mantiene un wake lock propio del servicio.

Prueba física pendiente: con tiempo de apagado corto, viaje y burbuja visibles, dejar Waze/otra app al frente más allá del tiempo; repetir con panel abierto/cerrado. Ocultar o detener debe liberar la protección de APP (otras apps pueden mantener su propia pantalla encendida). Pulsar bloqueo debe apagar la pantalla y retirar la burbuja. Verificar que mostrar/mover no inicia escucha.

La prueba conjunta de Fase 2 continúa en feature/fase-2-waze-screen desde b862acf. Añade controles de lectura de pantalla en Navegación con desarrollo activo y una última indicación observada en Viaje, reutilizando las mismas superficies, colores y tamaños. No modifica el carrusel ni los controles de la burbuja. Ver [fuente, consulta y prueba](FASE_2_PANTALLA_WAZE.md).

Rama: `feature/ui-storm-glass`, basada en `feature/fase-2-waze-observation`
`f10672490a2a0951195db5375d415adf1240a111`. Esa base contiene Ajustes separados,
la Fase 2 de contexto de navegación y la observación opcional de Waze.

## Material y colores

El material se centra en azul profundo y reflejos celestes. El texto permanece opaco;
la transparencia se aplica a la superficie y sus capas. El fondo, los tintes y el
acabado de paneles se centralizan en `StormGlass.kt` y `Glass.kt`.

| Elemento | Claro | Oscuro |
| --- | --- | --- |
| Fondo | `#D9E7F4` | `#061426` |
| Tinte de vidrio | `#E0F0FF`, alpha 0.72/0.84 | `#123A60`, alpha 0.46/0.64 |
| Botón principal | `#075BA8`, texto blanco | `#226BB5`, texto blanco |
| Texto principal | `#102D46` | `#EAF5FF` |

Un reflejo diagonal estático detrás del contenido, borde de luz y sombra moderada
aportan profundidad. Las acciones secundarias tienen tinte azul y borde; el estado
deshabilitado sigue siendo reconocible. La ventana flotante conserva su esfera,
posición, dimensiones y controles; el panel y botones cambian a la misma dirección azul.
El icono conserva su forma con fondo azul. No hay dependencias decorativas nuevas,
captura de otras apps, desenfoque de texto ni animación continua durante el audio.

## Carrusel real en Inicio

El panel principal y la preparación del equipo se organizan en tres tarjetas:

- **Copiloto:** estado real, modo, aviso del casco y botón existente para iniciar/abrir Viaje.
- **Mi equipo:** casco seleccionado/disponible, modo, reproductor preferido y accesos
  existentes a configuración, rol de asistente y ayuda de compatibilidad.
- **Mi ruta:** última solicitud real de navegación, con sus límites de confirmación,
  y acceso a Viaje. No inventa ETA, kilómetros ni estado de ruta activa.

En la ampliación de Fase 2 para Google Maps, Mi ruta y Viaje nombran el proveedor
del último despacho real. Ajustes → Navegación usa las mismas tarjetas y selección
azul para elegir Waze o Maps; los campos de direcciones de Maps solo se muestran
cuando ese proveedor está seleccionado. Una lectura experimental de Waze no se
presenta como indicación de Maps. Alcance y prueba en [Google Maps](FASE_2_GOOGLE_MAPS.md).

Se puede deslizar horizontalmente o pulsar pestañas etiquetadas de al menos 48 dp.
Con fuente de 130 % o mayor, las pestañas pasan a una lista vertical para conservar las palabras completas.
La página elegida se conserva con el estado guardable de Compose; la altura se adapta
al contenido y la fuente. El resto de Inicio continúa desplazándose verticalmente.
Los accesos a Comandos, permisos pendientes y ayuda permanecen fuera del carrusel.
La barra principal mantiene Inicio/Viaje/Ajustes; no se añaden pantallas ficticias.

Deslizar o cambiar pestaña solo mueve la presentación. No llama al servicio, cambia
modo/casco, abre micrófono, inicia/detiene viaje ni despacha acciones externas.
Los botones de acción conservan sus callbacks, estados y requisitos existentes.
Mi moto e Historial siguen pendientes de sus datos y lógica; no se presentan como
funciones de esta entrega. La observación experimental de Waze conserva el acceso
de desarrollador y no se convierte en navegación activa por aparecer esta tarjeta.

## Validación

Se compilaron debug, release y fieldTest con la dependencia real openwakeword-android
0.1.2; los tres APK se comprobaron como ZIP válidos. Las pruebas finales tienen 221 casos
por variante: debug 220 aprobados y 1 omitido; release 204 aprobados y 17 omitidos,
sin fallos ni errores. Debug omite el caso exclusivo de release; release omite las
pruebas Compose que necesitan el host exclusivo de debug. Los seis casos nuevos del
carrusel y los cuatro de contraste pasaron. Lint debug terminó con cero errores,
110 advertencias y 9 recomendaciones. No se atribuyen resultados de entregas previas
a la paleta ni al carrusel nuevos.

Se inspeccionaron renders de Inicio en claro/oscuro, equipo y ruta con fuente ampliada.
En fuente grande las pestañas mantienen palabras completas y acceso táctil; se verifica
la línea pintada con tolerancia de un píxel para el redondeo de medidas de texto.
Se eliminó el mensaje duplicado de preparación en el estado de reposo de Inicio.
Las opciones de viaje, solicitudes pendientes y avisos de problema siguen disponibles.

La herramienta local usa JDK 17, SDK 36 y Gradle 8.13. Los ajustes del proxy/certificados,
repositorios y límites de workers de este entorno no se añaden al proyecto.

Las pruebas añadidas usan la UI Compose real en Android JVM: gestos, selección de
pestañas, ausencia de callbacks de audio durante swipes, arrastre sobre Iniciar,
estado de viaje activo y fuente al 200 % en 320 dp. Los tests de contraste utilizan
los tintes reales, la combinación máxima de gradientes, reflejos y botones azules.
Los renders proceden del View Android real en Robolectric, no son mockups ni fotos
del S22+. La regresión física de tacto, TalkBack, sol y fluidez con audio sigue pendiente.

## Probar en Android Studio y S22+

```bash
git fetch origin
git switch --track origin/feature/ui-storm-glass
```

Si la rama ya existe localmente, cambia a ella y ejecuta `git pull --ff-only`.
Sincroniza con JDK 17/SDK 36 y ejecuta debug o fieldTest.

1. Abre Inicio en ambos temas: botones azules, tinte bajo el vidrio, texto legible.
2. Desliza Copiloto → Mi equipo → Mi ruta y vuelve mediante las pestañas. Comprueba
   el desplazamiento vertical y que arrastrar sobre Iniciar no lo active.
3. Inicia un viaje deliberadamente. Cambiar tarjetas debe conservar sesión/audio;
   vuelve a Viaje mediante su acceso. Repite con Y12 Pro y Dime Evo.
4. Revisa texto al 150/200 %, orientación, TalkBack y sol directo; confirma que los
   botones y pestañas siguen alcanzables. Revisa burbuja sobre Waze en claro/oscuro.
5. Repite una orden, cancelación y llamada para comprobar recuperación de música;
   revisa Ajustes y que las herramientas internas permanezcan protegidas.

El clima, el historial y el perfil de moto no se implementan en esta entrega visual.

## Archivos de la entrega

- `AGENTS.md`
- `app/src/main/java/com/gust4av033/appcopilotomoto/OverlayGlassStyle.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/components/CopilotComponents.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/components/HomeDashboardPager.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/components/JourneyHero.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/screens/HomeScreen.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/theme/CopilotPalette.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/theme/CopilotTheme.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/theme/Glass.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/theme/StormGlass.kt`
- `app/src/main/res/values/brand_colors.xml`
- `app/src/main/res/values/ui_strings.xml`
- `app/src/test/java/com/gust4av033/appcopilotomoto/HomeDashboardUiTest.kt`
- `app/src/test/java/com/gust4av033/appcopilotomoto/SettingsContrastTest.kt`
- `docs/UI_AZUL_TORMENTA.md`
- `docs/UI_BASE.md`
