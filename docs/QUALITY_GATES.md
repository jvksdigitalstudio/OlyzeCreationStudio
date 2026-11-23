# Gates de calidad (Fase 2)

Qué comprueba el CI, cómo se activa cada gate y por qué se eligieron estas herramientas. Decisión: [ADR-021](adr/ADR-021-gates-de-calidad.md).

## 1. Principio: no regresión, no "arreglarlo todo ya"
El código existente (82 000 líneas) tiene deuda de estilo y de lint. Bloquear por ella detendría todo el trabajo. Cada gate usa un **baseline**: la deuda actual queda congelada y **solo fallan los problemas nuevos**. Después se salda por etapas (borrando entradas del baseline al corregirlas).

## 2. Los gates

| Gate | Herramienta | Config | Baseline | Cuándo bloquea |
|---|---|---|---|---|
| Estilo | **ktlint 1.8.0** (CLI, versión fija) | `.editorconfig` | `ktlint-baseline.xml` (raíz) | Con baseline presente: infracción nueva |
| Lint de Android | **Android Lint** (AGP, `lintDebug`) | `lint {}` en `app/build.gradle.kts` | `app/lint-baseline.xml` | Con baseline presente: problema nuevo |
| Release | R8 + `check_release_*` + `zipalign` | ver [RELEASE_ENGINEERING](RELEASE_ENGINEERING.md) | — | Siempre |
| Tests | JUnit (1 700+) + núcleo nativo con sanitizadores | — | — | Siempre |

## 3. Cómo se activan (una sola vez)
Hasta que existan los baselines, ambos gates corren en **modo informativo**: no fallan, **generan** el baseline y lo suben como artefacto.
1. Ejecuta el CI. Descarga el artefacto **`olyze-quality`** (`quality/ktlint-baseline.xml`, `quality/lint-baseline.xml` y el reporte HTML de lint).
2. Sube `ktlint-baseline.xml` a la **raíz** del repositorio y `lint-baseline.xml` a **`app/`**.
3. A partir del siguiente CI ambos son **bloqueantes** para problemas nuevos. No hay que editar el workflow.

Mientras falte un baseline, el CI muestra un aviso amarillo que lo indica.

## 4. Por qué no detekt
- La rama estable (1.23.x) está compilada contra Kotlin 2.0; este proyecto usa Kotlin 2.3. Esa combinación no es fiable.
- La rama 2.x que soporta Kotlin 2.3/2.4 **sigue en alfa** (última: `2.0.0-alpha.5`, 2026-06-17).
- Política del proyecto: nada experimental en un gate. **Se revisará cuando detekt 2.0 sea estable.** ktlint trae su propio compilador, así que no depende de la versión de Kotlin del proyecto.

## 5. Regenerar baselines tras mover código
> **Durante la Fase 3 (partición de `EditorScreen.kt`) no se usa este flujo.** Cada corte invalida los baselines y, además, el flujo de subida del proyecto borra los que no vienen en el zip (aviso «Falta … baseline» en el CI). Los gates quedan en modo informativo hasta terminar la Fase 3; entonces se generan los baselines definitivos una sola vez y se incluyen en el zip. Lo que sigue es el procedimiento para cuando haga falta.

El baseline de ktlint identifica cada infracción por **archivo + línea + regla**. Al mover o partir código (Fase 3) las líneas se desplazan y las infracciones ya conocidas parecen "nuevas": el CI sale rojo en ktlint aunque no haya nada nuevo. En ese caso:
1. Actions → **Regenerar baselines de calidad** → *Run workflow* (rama `main`).
2. El workflow regenera `ktlint-baseline.xml` y `app/lint-baseline.xml`, los publica en un commit y **lanza el CI principal**.
3. **Salvaguarda:** falla si el baseline **crece** (una refactorización pura no añade infracciones). Si crece, hay infracciones reales: se corrigen. Solo con `allow_growth = true` se aceptan a conciencia.

Úsalo **solo** tras cambios estructurales. No sirve para tapar problemas introducidos en el mismo cambio.

## 6. Mantenimiento
- **Subir ktlint:** cambia `KTLINT_VERSION` en el workflow. Una versión nueva puede añadir reglas: regenera el baseline en el mismo cambio.
- **Fijar el binario:** el paso imprime el SHA-256 de ktlint; pégalo en `KTLINT_SHA256` para que el CI falle si el binario cambia.
- **Saldar deuda:** corrige un archivo, borra sus entradas del baseline (o regénéralo) y confirma.

## 7. Auditorías propias (informativas)
`tools/audit/run_all.sh` (imports sin uso, código muerto, `catch`, funciones largas, clones). Son **heurísticas**: señalan candidatos que hay que verificar a mano (ver ADR-021 §3: dos de sus alertas resultaron falsos positivos).

## 8. Tests que leen el código como texto
154 de 1 743 tests (19 archivos) comprueban el **cableado** leyendo el fuente. Es válido para invariantes de configuración (Gradle, manifest, R8), pero los de la pantalla del editor se rompían al mover código entre archivos. Ahora leen por `ProjectFiles.editorScreen()`, que reúne `ui/EditorScreen.kt` y todo `ui/editor/**`: partir el archivo (Fase 3) ya no los rompe. Los de comportamiento real de UI requieren pruebas instrumentadas (siguen pendientes; necesitan emulador en CI).
