# ADR-021 — Gates de calidad (Fase 2)

- **Estado:** Implementado (2026-11-22). **Pendiente de la primera ejecución real en CI** (genera los baselines).
- **Alcance:** estilo (ktlint), Android Lint, protección de los tests de cableado frente al refactor, StrictMode en debug.
- **Fuera de alcance:** tests instrumentados (necesitan emulador en CI), refactor de `EditorScreen` (Fase 3).

## 1. Contexto — hechos verificados
| # | Hecho | Fuente |
|---|---|---|
| 1 | No había `.editorconfig`, ktlint, detekt ni configuración de Lint. | repo |
| 2 | Longitud de línea del código: p99 = 125, máximo 322; 27 imports con comodín; 0 tabuladores. | medido |
| 3 | detekt 1.23 (estable) va compilado contra Kotlin 2.0; la 2.x que soporta Kotlin 2.3+ es **alfa** (última 2.0.0-alpha.5, 2026-06-17). | detekt.dev / Gradle Plugin Portal |
| 4 | El CLI de ktlint crea el baseline si no existe (`--baseline=archivo`) y 1.8.0 es estable. | docs de ktlint |
| 5 | 154 de 1 743 tests leen el fuente como texto; 11 archivos leen `EditorScreen.kt`. | medido |

## 2. Decisión
1. **ktlint 1.8.0 por CLI** (sin plugin de Gradle: cero riesgo para la build de la app, independiente de la versión de Kotlin) + `.editorconfig` (`android_studio`, 140 columnas, `@Composable` exento del naming).
2. **Android Lint** de AGP, con baseline que solo se asigna si existe (apuntar a uno inexistente haría que `assembleRelease` —que corre `lintVital`— intentara crearlo y fallara).
3. **Modo de activación automático**: sin baseline el gate es informativo y lo genera como artefacto; con baseline es bloqueante. No requiere editar el workflow.
4. **No usar detekt** hasta que 2.0 sea estable (política: nada experimental en un gate).
5. Los tests de cableado del editor leen por `ProjectFiles.editorScreen()` (independiente de cómo se reparta el código en archivos).
6. **StrictMode solo en builds depurables**, registrando en `AppLogger` (visible en la app, sin Logcat), con deduplicación y tope.

## 3. Correcciones a mi auditoría inicial (verificadas en el código)
- **`catch (Throwable/Exception)` y cancelación:** de 74 sitios, 8 eran candidatos y **los 8 son falsos positivos** (`persistNow` ya relanza `CancellationException`; el resto solo envuelve llamadas bloqueantes que no pueden lanzarla). No hay defecto; no se tocó producción.
- **26 llamadas "en hilo principal" (auditoría propia del repo):** falsos positivos; todas son `suspend fun … = withContext(Dispatchers.IO)`.
- **Tests "Structure":** son el 9 % (154/1 743), no la mayoría; el resto son tests de comportamiento.

## 4. Consecuencias
- El primer CI tras este cambio **no fallará por estilo ni lint** (modo informativo); deja un aviso hasta subir los baselines.
- Los baselines pueden ser grandes: es la deuda real medida por primera vez. Es una foto, no un fallo.

## 5. Lo que NO se ha verificado
- **No se ejecutó ktlint ni Lint** (sin red). Verificado: sintaxis del workflow (YAML), cálculo de estilo sobre el código, y el estático del repo. La config de ktlint (`ktlint_function_naming_ignore_when_annotated_with`, `android_studio`) es de su documentación; si el primer CI la rechaza, se ajusta el `.editorconfig`.
- `-Dlint.baselines.continue=true` y `lint {}` (`htmlReport`/`xmlReport`/`baseline`) son del DSL de AGP 8.x; el primer CI lo confirma.
- StrictMode no se ejecutó en un dispositivo.
