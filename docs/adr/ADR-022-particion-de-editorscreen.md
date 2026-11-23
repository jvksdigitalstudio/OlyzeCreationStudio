# ADR-022 — Partición de `EditorScreen.kt` (Fase 3)

- **Estado:** En curso. Cortes 1 y 2 compilados y en verde en CI (runs #684 y #685, 2026-11-22; pendientes de probar en dispositivo). Corte 3 hecho, pendiente de CI.
- **Problema (medido):** `ui/EditorScreen.kt` tiene 18 160 líneas (1 MB). Su función `EditorScreen` ocupa 6 063; `FloatingToolWindow` 1 270; otras 44 funciones superan las 150 líneas. Es inmantenible, ralentiza la compilación y concentra el riesgo de cada cambio.

## Decisión de método
1. **Mover, no reescribir.** Cada corte traslada un bloque contiguo y autocontenido **tal cual**. Cero cambios de lógica; la única edición permitida es subir de `private` a `internal` lo estrictamente necesario.
2. **El paquete no cambia** (`com.yeivikas.olyzecs.ui`); los archivos nuevos van en la carpeta `ui/editor/`. Ningún símbolo cambia de nombre ni de ámbito, no hay que tocar imports en el resto del código y los tests de cableado (que leen por `ProjectFiles.editorScreen()`) siguen válidos.
3. **Cada corte lo aplica `tools/refactor/move_block.py`**, que analiza, mueve y VERIFICA (y restaura el original si algo no cuadra): mismo multiconjunto de declaraciones, bloque idéntico línea a línea salvo la visibilidad, `EditorScreen.kt` = original − bloque − imports huérfanos, y analizador estático del repo. Su análisis calcula también el **cierre transitivo** de tipos `private` que quedarían expuestos en una firma `internal`.
4. **Los imports** del archivo nuevo son los que el bloque usa; de `EditorScreen.kt` solo se podan los que quedaron huérfanos por el movimiento (no se mezcla limpieza con refactorización).
5. **Presupuesto de tamaño** (`RefactorProgressStructureTest`): el archivo solo puede bajar; y ninguna declaración puede estar repetida entre `EditorScreen.kt` y `ui/editor/`.
6. **Baselines de ktlint/lint durante la Fase 3:** los gates se quedan en modo INFORMATIVO. Los baselines identifican infracciones por archivo + línea (ktlint) o archivo (lint), así que cada corte los invalidaría, y el flujo de subida del proyecto borra los baselines que no vienen en el zip (observado en el run #684: «Falta ktlint-baseline.xml»). Al terminar la Fase 3 se generan los baselines definitivos una sola vez y se incluyen en el zip. El workflow manual *Regenerar baselines de calidad* sigue disponible.

## Hallazgo del corte 2: el acoplamiento por `private`
Casi todo `EditorScreen.kt` está acoplado por símbolos `private` de nivel superior (el archivo es una única unidad de compilación). Medido sobre las 100 formas posibles de cerrar el bloque que empieza en `RecolorFloatingWindow`: el mínimo es **12 símbolos a subir de `private` a `internal`** para un bloque de ~970 líneas, y crece hasta 47 para bloques de ~7 000. Es el coste inevitable de partir el archivo; no altera el comportamiento (siguen siendo invisibles fuera del módulo) y queda registrado en la cabecera de cada archivo nuevo. Consecuencia de método: **se mueven clústeres cohesivos, no los bloques más grandes**, y la visibilidad se estrecha de nuevo cuando cada clúster viva en su propio archivo (p. ej. `EffectsControlsState` y sus paneles juntos).

## Registro de cortes
| # | Bloque | Destino | Líneas movidas | `EditorScreen.kt` | Visibilidad cambiada |
|---|--------|---------|---------------:|------------------:|----------------------|
| 0 | (original) | — | — | 18 160 | — |
| 1 | Infraestructura de la ventana flotante: constantes, `FloatingWindowMinimizedRegistry`, cabecera, `FloatingToolWindow`, menú de opciones, presets, insignia minimizada, zona de borrado | `ui/editor/FloatingWindow.kt` | 2 454 | 15 684 | `FloatingWindowDeleteDropZone` (`private`→`internal`; la usa `EditorScreen`) |
| 2 | Ventanas de efectos `RecolorFloatingWindow`, `ColorBasicoFloatingWindow`, `Basico3DFloatingWindow` (+ constantes) | `ui/editor/ColorAnd3DEffectWindows.kt` | 967 | 14 713 | 12: las 3 ventanas y `COLOR_BASICO_FLOATING_WINDOW_DEFAULT_SIZE`, más `ColorMenuBasicoIcon`, `ColorMenuRecolorIcon`, `EffectsCategoryColor`, `EffectsControlsState`, `Extrude3DGestureBridge`, `Menu3DBasicCubeIcon`, `colorDistanceSquared`, `rememberEffectsControlsState` |
| 3 | **Panel de efectos y sus ventanas**: ventanas `Contorno`, `Resplandor`, `Sombra`, `Reflejo`, `Distortion`; `EffectsControlsState`, `buildFullEffectsParams`, `EffectsPanel`, las 11 `EffectsCategory*`, `ThreeWayToggle`, `EffectsCategoryTabs`, `ColorSwatchPickerButton` | `ui/editor/EffectsPanelAndWindows.kt` | 3 321 | 11 391 | 14: 6 en el bloque (5 ventanas + `EffectsPanel`) y 8 en `EditorScreen.kt` (`BelowAnchorCenteredPopupPositionProvider`, `DistortionGestureBridge`, `DistortionPanel`, 5 iconos `EffectMenu*Icon`) |

## Aprendizaje del corte 3: cortar por cohesión, no por tamaño
Mover solo las cinco ventanas de efectos costaba 18 cambios de visibilidad (~420 líneas); incluir también el panel que usan (estado, categorías, pestañas, selector de color) bajó a **14 para 3 321 líneas**: la frontera correcta pasa por detrás de las dependencias. La herramienta mide cada límite posible; el criterio es **líneas movidas por cambio de visibilidad**, no el tamaño.

## Próximos cortes (candidatos)
1. **Distortion**: `DISTORTION_MAX_UNDO_STEPS`, `DistortionToolChip`, `DistortionPanel` (643), `DistortionDirectionToggle`, `DistortionSmallActionButton` (~700 líneas).
2. **Menús de edición y sus iconos**: `EditImageEffectsMenu`, `EditImageColorMenu`, `EditImage3DMenu`, sus `*MenuItem` y los `EffectMenu*Icon`/`Menu3D*Icon`/`ColorMenu*Icon` (~1 000 líneas); aquí se podrán **estrechar de nuevo a `private`** los iconos hoy `internal`.
3. Gestos y geometría (`layerBoundingQuadPx`, `hitTestLayerAt`, `screenPointToLayerUv`, `clampedHandleSlots`, rejilla `GridSpec`/`drawGridGuides`…).
4. **La función `EditorScreen` (~6 000 líneas) al final**: el corte más arriesgado, cuando ya haya archivos pequeños.

## Lo que NO se ha verificado
- **No se compiló** (sin red en el entorno de trabajo). Se verificó la estructura (ver método §3) y el analizador estático; el riesgo residual son errores que solo ve el compilador (un import necesario que el análisis léxico no detecte, o una referencia a un símbolo `private`). Mitigación: el análisis de dependencias dio 0 privados de fuera usados por el bloque y 1 privado del bloque usado fuera.
- El comportamiento en dispositivo: al ser un movimiento sin cambios de lógica no debería variar, pero debe probarse (abrir una ventana flotante, arrastrarla, minimizarla, soltarla en la zona de eliminar, abrir "Opciones" y "Presets").
