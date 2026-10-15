# Auditoría de ramas de Fase 2 · 14 de octubre de 2026 (UTC)

Instantánea revisada el 14 de octubre a las 04:19 UTC. `main` era y sigue siendo la rama por defecto. No se fusionó Fase 2 a `main` ni se reescribió historia.

## Inventario anterior

| Rama remota | HEAD anterior | Resultado del análisis |
| --- | --- | --- |
| `main` | `caf08d2` | Base estable; se conserva sin cambios. |
| `release/fase-1-rc1` | `d82c017` | Integrada en `main` por #14. |
| `feature/settings-user-developer` | `10fd6f9` | Integrada en RC1 por #13 y en `main`. |
| `feature/fase-2-navigation-context` | `78777b3` | Integrada en `main` por #15. |
| `feature/fase-2-waze-observation` | `f106724` | Entrega experimental, ancestro de la base integrada. |
| `feature/ui-storm-glass` | `b862acf` | Glass UI y carrusel, ancestro de la base integrada. |
| `feature/fase-2-waze-screen` | `4baf1cb` | Su código funcional hasta `15888f1` estaba en la rama más reciente; `4baf1cb` modificaba solo `AGENTS.md` y se incorporó por merge para preservar su historial. |
| `feature/fase-2-google-maps-provider` | `b949cd6` | Maps y navegación, ancestro de la base integrada. |
| `feature/ui-midnight-glass-v2` | `f1bda4c` | UI v2 más reciente; base del árbol funcional consolidado. |

Los PR #16–#20 estaban abiertos como borradores apilados. Sus cambios funcionales formaban una cadena hasta `f1bda4c`; el commit lateral `4baf1cb` y el commit de `main` `caf08d2` se incorporaron con merges normales. Los conflictos afectaban únicamente `AGENTS.md`; quedó la guía de v2. Antes de borrar cada rama se comprobó que su HEAD remoto era ancestro del nuevo HEAD, y cada borrado se protegió contra cambios remotos posteriores.

## Resultado

Solo quedan `main` y `feature/fase-2-integrada`. La nueva rama incluye todos los HEAD anteriores por ascendencia y conserva el código y las capturas de `f1bda4c`; solo se actualizaron referencias de documentación para reflejar la estructura nueva. El [PR #21](https://github.com/Gust4av033/APP-Copiloto-Moto/pull/21) es el único PR abierto, en borrador y dirigido a `main`. Los PR #16–#20 se cerraron con referencia a #21. Se retiraron las ocho ramas anteriores, sin fusionar código no validado ni alterar `main`.

## Verificación y siguiente paso

Con JDK 21 y dependencias reales disponibles, `assembleDebug`, `assembleFieldTest` y `testDebugUnitTest` terminaron correctamente: 237 pruebas, 236 aprobadas, una omitida y cero fallos/errores. `lintDebug` terminó con cero errores, 125 advertencias y nueve sugerencias. `git diff --check` quedó limpio. Se revisaron las capturas registradas en [Glass UI v2](UI_TORMENTA_MEDIANOCHE_V2.md) para Inicio, Viaje y Ajustes en ambos temas y fuente al 200 %.

Antes de integrar #21 faltan pruebas físicas de Waze y Maps en el Samsung, permisos y revocación, burbuja sobre el navegador, voz y audio Bluetooth, TalkBack, brillo exterior y fluidez de vidrio FULL/LITE. Las capturas y pruebas JVM no sustituyen esa validación.

## Continuidad de Fase 2 en la rama de UI reciente

Después de aquella instantánea, `feature/fase-2-integrada` avanzó hasta `f65378d` y apareció `feature/ui-storm-refined`, descendiente de la integrada, con Glass UI más reciente. Por elección explícita del usuario, el cierre del contexto se desarrolló en `feature/ui-storm-refined` sin abrir otra rama de Fase 2. `main` sigue estable. Este apartado registra el estado posterior y no altera el inventario histórico anterior.

| Estado | Rama/PR | Resultado |
| --- | --- | --- |
| Antes de este cierre | `main` `caf08d2` | Base estable y rama por defecto. |
| Antes de este cierre | `feature/fase-2-integrada` `f65378d`, PR #21 a `main` | Todo su HEAD quedó probado como ancestro de la rama UI. |
| Antes de este cierre | `feature/ui-storm-refined` `35b8546`, PR #22 a integrada | Siete commits posteriores, Glass UI refinada y base funcional más reciente. |
| Después | `main` `caf08d2` | Sin cambios, sin merge de código no validado. |
| Después | `feature/ui-storm-refined` desde `3e35d55` | Única rama funcional de Fase 2; recibe dos commits de este cierre. |
| Después | PR #22 a `main` | Único PR abierto, aún en borrador para la validación física. |
| Después | PR #21 | Cerrado sin fusionar; sustituido por #22. |

`feature/fase-2-integrada` se eliminó del remoto después de comprobar que `f65378d` era ancestro de la rama UI y de proteger el borrado contra un cambio remoto posterior del HEAD. No se reescribió historia; sus commits siguen en la rama UI y en el PR cerrado. El commit funcional inicial del cierre es `3e35d55`.

El cierre de Fase 2 agrupa fuentes observadas de Waze y Maps, multimedia, estado del dispositivo, destinos con coordenadas opcionales y GPS local opt-in. Cada valor conserva procedencia, antigüedad monotónica, confianza y caducidad. La fusión da prioridad a muestras frescas de mayor confianza; no convierte una notificación o un despacho en ruta confirmada. La Capa 1 es GPS/sensores; Capa 2, calle bajo las ruedas con OSM/Valhalla futuro; Capa 3, ruta propia estimada a coordenadas (hoy distancia lineal); Capa 4, navegador externo observado; Capa 5, servicios de tráfico de pago futuros. La grabación queda en almacenamiento interno, replay aislado del historial, y la detección de movimiento solo registra eventos en sombra.

En ambos sabores, `testFullDebugUnitTest` y `testPlaystoreDebugUnitTest` finalizaron con 257 pruebas cada uno, cero fallos (dos y tres omitidas, respectivamente). `lintFullDebug` y `lintPlaystoreDebug` terminaron con cero errores; conservan 132 y 134 advertencias. La compilación de ambos sabores y las pruebas de UI/contraste incluidas en las suites JVM pasaron. No había teléfono ni emulador configurado para una captura o recorrido real.

### MOD-009 — Tráfico colaborativo y calibración personal (RESERVADA)

Estado reservado, no aprobado para implementar; relacionado con Fase 9 historial, Fase 11 cuenta y Fase 13 tráfico, no con Fase 8 Seguridad. Secuencia obligatoria: 1) calibración personal local por tramo/hora que mejore ETA con un usuario y cubra arranque en frío; 2) detección en sombra y medición de falsos positivos; 3) confirmación por voz uno/dos; 4) opt-in separado y backend anónimo. Lento no implica tráfico (semáforo, tope, lluvia); tráfico para moto difiere del de auto por filtrado entre carriles. Nunca confirmar en curva o a alta velocidad; también registrar «no». `PrivacyTrim` recorta 300 m de ambos extremos antes de cualquier uso colaborativo.

### Comprobación física pendiente

En el S22+ de San Salvador, con casco y Waze al frente: activar GPS desde Privacidad, conceder ubicación precisa, recorrer un tramo conocido y pedir «próximo giro», «cuánto falta» y «estado del viaje». Comparar giro/ETA con la pantalla real, exportar GPX y JSON, comprobar el recorte de 300 m y los eventos de lentitud/parada en Diagnóstico, medir batería y repetir con permiso revocado, pantalla bloqueada y variante `playstore`. Validar que micrófono, botón, SCO, burbuja y notificación mantienen su comportamiento. No integrar a `main` antes de esta prueba.
