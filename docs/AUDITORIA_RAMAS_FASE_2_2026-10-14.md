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
