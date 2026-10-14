# Ajustes de usuario y herramientas internas

Rama: `feature/settings-user-developer`. Base: `release/fase-1-rc1` (`c2415f3`).

RC1 contiene todos los avances de `feature/ui-glass-redesign` (dos commits por delante,
ninguno pendiente de integrar) y `feature/fase-1-intent-router` (siete por delante).
Se conservan el glass morado/celeste/azul, la burbuja de agua compacta y la lógica de Fase 1.
La reconstrucción de octubre 13 se valida de nuevo: las pruebas de un entorno anterior
no son evidencia de esta entrega.

## Estructura y capacidades reales

| Categoría | Opciones y comportamiento |
| --- | --- |
| Voz y activación | Voz desde el casco o botón; Alexa/Hey Jarvis, sensibilidad Baja/Equilibrada/Alta; cinco asignaciones multimedia existentes; rol de asistente como alternativa; comprobación y preparación de idiomas compatible con Android. |
| Audio y dispositivos | Selección automática o manual del casco; nombres comprensibles; selección persistente segura; reproductores existentes, incluido Poweramp; acceso multimedia; ayuda guiada. |
| Apariencia | Burbuja existente, permiso para superposición, volver a mostrarla y mantener pantalla encendida durante el viaje. El tema sigue al sistema; no se inventan controles de tema. |
| Navegación | Disponibilidad real de Waze, comandos existentes, solicitud de destino y eliminación de esa solicitud. No se ofrece un selector de proveedores que el runtime no soporta. |
| Integraciones | Disponibilidad real de mensajería y aplicaciones; permisos de contactos; submenú de llamadas y acceso a audio/música; catálogo de comandos. |
| Privacidad y permisos | Estado de micrófono, dispositivos cercanos, notificaciones, contactos, teléfono, acceso multimedia y superposición; acceso a la configuración de esta instalación en Android; explicación del estado actual de los controles de datos. |
| Acerca de APP | Versión instalada real, información general, ayuda de compatibilidad y mecanismo deliberado de acceso interno. |

La sensibilidad mantiene los valores del motor: Baja=60, Equilibrada=80 (predeterminado
anterior), Alta=85. El umbral de detección sigue siendo `(100 - sensibilidad) / 100`.
Los valores personalizados existentes se conservan; el selector muestra el preset más cercano.
La precisión numérica permanece disponible en debug con acceso de desarrollador.
Solo Alexa y Hey Jarvis tienen modelos implementados; no se ofrece texto libre como wake word.

## Separación funcional

- Usuario: preferencias reales, estados comprensibles y permisos por función.
- Diagnóstico básico: conexión, lista guiada de recuperación, acceso multimedia y resumen
  de versión/modo/disponibilidad/permisos. No abre micrófonos ni solicita foco de audio.
  El resumen no incluye contactos, mensajes, órdenes, IDs Bluetooth ni logs internos.
- Desarrollador: métricas, rutas SCO/LE, lecturas y comparación de audio, informe completo,
  eventos de botones, prueba de comandos, captura de cinco segundos, pruebas de wake word
  y anotaciones del protocolo. Las capacidades existentes se conservan.

Las rutas antiguas de Pruebas/Herramientas se resuelven a ayuda básica/Acerca de APP
cuando el acceso está desactivado, incluso después de restaurar el estado de navegación.
Las comprobaciones se realizan también en callbacks y métodos del runtime de la Activity.
El servicio limita el modo experimental, incluida su activación por voz, a debug con opt-in.
No se depende solamente de ocultar botones.

## Acceso de desarrollador

Con la moto detenida y sin viaje/captura activos:

1. Abre Ajustes → Acerca de APP.
2. Pulsa siete veces seguidas **Versión instalada**, con un máximo de dos segundos entre
   pulsaciones y quince segundos para la secuencia completa.
3. Confirma explícitamente en el diálogo.
4. Abre Herramientas de desarrollador → Pruebas.

Cancelar o abandonar la Activity descarta la secuencia; no se restaura desde un Bundle.
El acceso comienza oculto tanto en debug como en release. En release, el opt-in permite
solamente consulta y copia de diagnósticos. Pruebas de micrófono, detector, botones y
opciones experimentales requieren debug. Las preferencias de acceso son independientes
para debug y release. El acceso se puede desactivar desde la página de desarrollador.
Es una separación de producto y prevención de activación accidental, no autenticación.

La activación normal por un intent del asistente del casco sigue disponible sin modo
desarrollador, tanto durante el viaje como para una orden corta explícita fuera de él.

## Persistencia y seguridad durante el viaje

La frase y sensibilidad se guardan en `ui_settings` y migran el antiguo Bundle una sola
vez cuando no existe una preferencia persistente. Las demás claves se conservan.
El casco manual guarda nombre, dirección y tipo; nunca persiste un ID temporal de Android.
Tras reiniciar el proceso, una coincidencia estable única resuelve el ID nuevo. Si el casco
falta, la identidad es ambigua o Android no proporciona dirección, se exige reconectar o
elegir de nuevo: no se selecciona otro casco silenciosamente.

Cambios de modo/frase/sensibilidad/casco/reproductor/botón/permisos quedan bloqueados
durante viaje, restauración o captura. La pantalla, burbuja y cancelación permanecen
disponibles según sus controles existentes. Los cambios de modo por voz siguen funcionando
para voz/botón. Las opciones experimentales no se anuncian como capacidades de usuario.

## Dime Evo: revisión del acceso multimedia

`TripButtonCoordinator.start()` comprueba `TripMediaAccess.granted()` antes de registrar
la sesión multimedia. El diagnóstico de eventos puede observar PREVIOUS aunque esa
autorización no esté concedida. Esto explica una causa posible del reporte del usuario;
no demuestra cuál es la causa física en sus EVO.

Android utiliza acceso especial a notificaciones para consultar sesiones multimedia;
el listener de APP no implementa lectura del contenido de notificaciones. El control está
visible en Audio y dispositivos y Privacidad, además de la configuración del botón.
El intent apunta al componente de la instalación actual: APP y `APP · pruebas` tienen
permisos distintos. Después de concederlo se actualiza el estado al volver a APP; hay que
iniciar un viaje nuevo para volver a registrar el botón.

## Validación de esta reconstrucción

Compilación comprobada de debug, release y fieldTest; los tres APK se verificaron como ZIP válidos.
Pruebas de esta reconstrucción: debug 199 casos (198 aprobados, 1 omitido), release 199 casos
(191 aprobados, 8 omitidos), sin errores ni fallos. Debug omite el caso exclusivo de release;
release omite las ocho pruebas Compose porque su host solo se incorpora a debug. La UI de
consulta de release se comprueba también en las pruebas Compose de debug, y las restricciones
de captura se comprueban ejecutando la Activity compilada de release.
Lint debug: 0 errores, 102 advertencias y 9 recomendaciones. Lint release: 0 errores,
101 advertencias y 9 recomendaciones. La última tarea de lint release terminó correctamente
sin red, utilizando las dependencias ya descargadas. Persisten avisos de lint, principalmente
sobre APIs obsoletas y tareas de mantenimiento; esta entrega no intenta corregirlos fuera
del alcance de Ajustes.
No se atribuyen resultados del intento anterior a esta reconstrucción.

Se verificó `BuildConfig.DEBUG=false` en la clase compilada de release y la ausencia del host
Compose de pruebas en los APK release/fieldTest. Los renders revisados provienen del View
Android de la UI Compose real en Robolectric, con temas claro/oscuro y fuente ampliada:
no son mockups, pero tampoco capturas de un teléfono. Las pruebas incluyen 320/360/800 dp,
fuente de hasta 200 %, objetivos táctiles de al menos 48 dp y contraste representativo de texto
sobre vidrio/botones de al menos 4.5:1. TalkBack, orientación y contraste sobre apps externas
requieren la revisión física indicada abajo.

Entorno de validación: JDK 17, Gradle 8.13, SDK 36, Build Tools 35 y la dependencia real
openwakeword-android 0.1.2; no se sustituyó por stubs. Los ajustes de proxy, certificados y
repositorios usados para este entorno son locales y no se incorporan al proyecto.

Las pruebas nuevas incluyen restricciones de acceso, navegación restaurada, SharedPreferences
con lectura real del archivo tras invalidar la caché, identidad del casco después de reinicio,
permisos por instalación, callbacks de la UI Compose, distintos tamaños/fuentes y contraste.
Robolectric es Android en JVM: estas pruebas no validan el audio Bluetooth ni un teléfono físico.

## Android Studio y prueba física

```bash
git fetch origin
git switch --track origin/feature/settings-user-developer
```

Si la rama ya existe localmente: `git switch feature/settings-user-developer` y después
`git pull --ff-only`. Conserva cambios locales antes de cambiar de rama. Abre el proyecto
en Android Studio, sincroniza con JDK 17 y SDK 36, selecciona variante debug o fieldTest
y ejecuta en el Samsung S22+. Release no tiene firma de distribución configurada.

```bash
./gradlew :app:assembleDebug :app:assembleFieldTest :app:assembleRelease
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease
```

En el teléfono, con la moto detenida:

1. Revisa las siete categorías y sus submenús. Cambia frase, sensibilidad, casco, reproductor,
   botón, burbuja y pantalla; fuerza cierre y vuelve a abrir para comprobar persistencia real.
2. Confirma que no aparecen pruebas, rutas técnicas ni métricas en la configuración normal,
   Inicio, Viaje ni ayuda básica. Prueba opt-in, cancelación, ocultación y navegación atrás.
3. En Dime Evo, elige **Anterior**, concede acceso multimedia a esa instalación y reproduce
   música en el reproductor seleccionado. Inicia un viaje nuevo en modo botón. Espera que
   esté listo, pulsa Anterior, di una orden y comprueba recuperación de música. Repite con
   pantalla apagada/bloqueada; prueba también Y12 Pro y la burbuja por separado.
4. Si sigue fallando, usa debug + acceso de desarrollador → Pruebas para registrar la tecla
   y copiar el informe completo. Revoca el permiso y comprueba el aviso antes de repetir.
5. Repite música/Poweramp, voz/botón, cambio de modo por voz, llamada y recuperación,
   WhatsApp/borrador/cancelación, Waze y acciones pendientes según la regresión RC1.
6. Revisa tema claro/oscuro, fuente grande, TalkBack, orientación y pantalla pequeña/tablet.

Todo el punto anterior requiere Samsung S22+, Y12 Pro/Dime Evo y aplicaciones reales.
No hay evidencia de validación física de esta entrega; la recuperación SCO/A2DP,
recepción de botones en bloqueo y comportamiento del firmware quedan pendientes.

## Actualización de distribución · UI Storm refined (14 de octubre de 2026)

La rama `feature/ui-storm-refined` agrupa la entrada de Ajustes en tres bloques:
**Casco y Botón Bluetooth**, **Navegación y Destinos** y **Voz, Permisos y
Privacidad**. Las siete páginas anteriores y Llamadas siguen accesibles como
subpantallas. La versión instalada conserva el acceso de siete pulsaciones de
desarrollador y ahora alterna `developerDiagnosticsUnlocked` con un Toast; debug
lo muestra por defecto y release lo oculta por defecto. Desactivar la vista de
diagnóstico no cambia la sesión, el micrófono ni los permisos. Los detalles
SCO/ID/métricas permanecen dentro de las pantallas de desarrollo.

| Capacidad | UI y permiso | Prueba |
| --- | --- | --- |
| Ajustes jerárquicos y retorno | Tres bloques → páginas existentes → volver al bloque | `SettingsAccessTest`, `SettingsNavigationUiTest` y renders de 320/360/800 dp |
| Diagnóstico persistente | Siete pulsaciones sobre versión; Toast de estado | `DistributionTest` y `ReleaseDiagnosticsTest` |
| Acceso a notificaciones | Diálogo antes de abrir Ajustes de listener multimedia/Waze | Comprobación del callback y manifiesto fusionado |
| Burbuja | Diálogo antes de solicitar superposición; cancelación deja desactivado el interruptor | Validación pendiente en Android real |
| Voz/contactos | Diálogo antes de pedir `RECORD_AUDIO` o `READ_CONTACTS` | Pruebas unitarias de permisos; lectura física pendiente |
| Política | Enlace en Voz, Permisos y Privacidad y en Privacidad | URL configurada; disponibilidad de GitHub Pages por confirmar |

El sabor `playstore` abre el marcador telefónico para que el usuario inicie
la llamada. `full` conserva la llamada directa. En ambos casos APP distingue
el envío de la solicitud del estado confirmado por Android. La recuperación
de SCO y el comportamiento de la apertura en segundo plano deben comprobarse
en el Samsung antes de publicar.

Para compilar esta rama, selecciona `fullDebug` para pruebas con todas las
herramientas o `playstoreDebug` para revisar el paquete de tienda. Para firmar
`playstoreRelease`, copia `keystore.properties.example` a `keystore.properties`
y coloca la ruta real del almacén y sus credenciales, o usa
`APP_SIGNING_STORE_FILE`, `APP_SIGNING_STORE_PASSWORD`,
`APP_SIGNING_KEY_ALIAS` y `APP_SIGNING_KEY_PASSWORD`. El archivo de propiedades
y los `.jks` están excluidos de Git. Sin credenciales se genera un release
sin firmar, que no se puede instalar directamente.

Verificación de esta actualización: `testFullDebugUnitTest` terminó con 247
casos (245 aprobados, 2 omitidos) y `testPlaystoreDebugUnitTest` con 247 (244
aprobados, 3 omitidos por pruebas exclusivas de accesibilidad `full`).
`ReleaseDiagnosticsTest` pasó en `playstoreRelease`; el manifiesto fusionado y
el APK debug de `playstore` carecen de permisos de llamada directa y del
servicio de accesibilidad. `assemblePlaystoreRelease` completó R8 y lint vital;
el APK release quedó sin firma por falta de credenciales. Las pruebas se
ejecutaron en Android JVM y no sustituyen la prueba de casco, SCO y marcador
en el Samsung. La URL pública de GitHub Pages tampoco se pudo comprobar desde
este entorno.

## Archivos de la entrega

- `.gitignore`
- `README.md`
- `app/build.gradle.kts`
- `app/src/main/java/com/gust4av033/appcopilotomoto/DeveloperAccess.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/HeadsetSelection.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/MainActivity.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/TripTestState.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/TripVoiceService.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/UserSettingsStore.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/CopilotApp.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/CopilotUiState.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/SettingsSection.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/components/AudioDevicesCard.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/components/CopilotComponents.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/components/DiagnosticsReportCard.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/components/HeadsetButtonProbeCard.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/components/JourneyHero.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/components/MusicPlayerCard.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/components/SettingsMenuRow.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/components/TripButtonSettingsCard.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/screens/CommandsScreen.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/screens/CopilotScreens.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/screens/HomeScreen.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/screens/UserSettingsScreens.kt`
- `app/src/main/java/com/gust4av033/appcopilotomoto/presentation/theme/CopilotTheme.kt`
- `app/src/main/res/drawable/ic_help.xml`
- `app/src/main/res/drawable/ic_info.xml`
- `app/src/main/res/drawable/ic_shield.xml`
- `app/src/main/res/values/ui_strings.xml`
- `app/src/test/java/com/gust4av033/appcopilotomoto/DeveloperAccessTest.kt`
- `app/src/test/java/com/gust4av033/appcopilotomoto/DeveloperRuntimeGateTest.kt`
- `app/src/test/java/com/gust4av033/appcopilotomoto/HeadsetSelectionStoreTest.kt`
- `app/src/test/java/com/gust4av033/appcopilotomoto/SettingsAccessTest.kt`
- `app/src/test/java/com/gust4av033/appcopilotomoto/SettingsContrastTest.kt`
- `app/src/test/java/com/gust4av033/appcopilotomoto/SettingsNavigationUiTest.kt`
- `app/src/test/java/com/gust4av033/appcopilotomoto/TripMediaAccessCompatibilityTest.kt`
- `app/src/test/java/com/gust4av033/appcopilotomoto/UserSettingsStoreTest.kt`
- `docs/AJUSTES_USUARIO_DESARROLLO.md`
- `docs/REGRESION_FASE_1_RC1.md`
- `docs/UI_BASE.md`
- `docs/regresion/F1_RC1_RESULTADOS.csv`
