# FASE 23 — Limpieza de código muerto y endurecimiento de nulos

**Método:** análisis estático (sin compilar: el entorno de auditoría no tiene
Kotlin/Android SDK). Cada eliminación se verificó con conteo de referencias
sobre `src/main` **y** `src/test`; solo se tocó lo que tenía 0 referencias.

## Código muerto eliminado
| Archivo | Elemento | Evidencia |
|---|---|---|
| `MainActivity.kt` | `eliNerAnimationApi`, `eliNerMesh3DApi`, `eliNerDistortionApi` (alias) | declarados, nunca leídos |
| `MainActivity.kt` | `eliNerTimelineApi` (construía un `TimelineApiImpl` por proyecto sin consumidor) | declarado, nunca leído |
| `engine/distortion/DistortionTools.kt` | `DistortionUv` | 0 referencias en main/test |
| `engine/render/GpuTextureLimits.kt` | `invalidateCache()` | 0 llamadores |
| `engine/render/RenderSnapshot.kt` | `RenderSnapshot.EMPTY` | 0 referencias |
| `viewmodel/EditorViewModel.kt` | `canMergeAudioClipWithNext()` | 0 llamadores (la UI resuelve lo mismo en `EditorScreen`) |
| `ui/AudioTrackRow.kt` | `import ...unit.Dp` | sin uso |

## Endurecimiento (NPE potenciales)
- `AudioProcessor.kt`: `format.getString(KEY_MIME)!!` → falla controlada con `fail(...)`.
- `EditorScreen.kt` (diálogo de grilla): dos `parsed!!` → `parsed?.takeIf { isValid }?.let(onConfirm)`.

## Revisado y conservado a propósito
- `Vec3.ZERO/cross/times`: API mínima coherente de un tipo matemático.
- `@color/brand_blue`: paleta documentada como espejo de `Theme.kt`.
- `!!` en `AudioProcessor.mixSources` (`sources[i]!!`): invariante garantizada (todo índice se llena o se retorna antes).

## Pendiente de validar por el equipo (no se pudo compilar aquí)
Ejecutar `gradle testDebugUnitTest assembleDebug` (CI existente).

## Corrección visual: columna de capas hasta el borde inferior
- Síntoma: bajo la columna de capas, a la altura de la barra Módulos/Control/Keyframes, quedaba un hueco (primero azul del degradado global, y pintarlo de morado no resolvía el pedido real): ahí debe verse la cabecera (miniatura) de la capa que sigue, no un relleno.
- Causa: `TimelineView` y `EditorBottomBar` eran hermanos en una `Column`; el timeline terminaba donde empezaba la barra, así que la columna de capas se cortaba 40dp antes del borde.
- Fix (estructural): el timeline ocupa todo el alto y la barra se superpone (`Box` + `align(BottomStart)`) solo sobre el área de pistas; su hueco izquierdo es transparente y no captura toques. `TimelineView` recibe `bottomInset = BOTTOM_BAR_HEIGHT` para que "+" y la última fila puedan subir por encima de la barra; el panel de pestañas abierto deja `bottom = BOTTOM_BAR_HEIGHT`.
- Archivos: `EditorScreen.kt`, `TimelineView.kt`, `EditorBottomBar.kt`.
