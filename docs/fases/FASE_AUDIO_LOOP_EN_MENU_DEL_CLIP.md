# Fase — Loop pasa del cajón «Módulos» al menú del clip (2026-10-18)

## Cambio
| Antes | Ahora |
|-------|-------|
| Módulos → Audio → **Loop** abría una ventana flotante con un interruptor («Repetir activado/desactivado»). | **Loop** se retiró del cajón. Mantener presionado un clip de audio → primer nivel del menú → **Activar loop / Quitar loop**. |

El cajón «Módulos → Audio» quedó con **Recorte** y **Fade**; Fade salió después (`FASE_AUDIO_FADE_EN_MENU_DEL_CLIP.md`) y Recorte también (`FASE_AUDIO_RECORTE_EN_VENTANA_DEL_CLIP.md`).

## Por qué
Loop es un estado **por clip** (`AudioClip.loop`), de dos valores, igual que Invertir / Silenciar / Normalizar, que ya viven en el menú del clip.
Una ventana flotante para un solo interruptor era más pasos (abrir cajón → elegir módulo → mover ventana) que un toque en el menú del clip
que ya se está usando. Además la ventana editaba el «clip objetivo» (`audioModuleTargetClip`), con riesgo de actuar sobre un clip distinto al tocado;
el menú actúa siempre sobre el clip pulsado.

## Diseño (mismo patrón que Invertir / Silenciar)
- `AudioClipEditActions.onToggleLoop(clipId)` — nuevo callback (`AudioTrackRow.kt`).
- Menú del clip, primer nivel, entre «Dividir en el cursor» y «Eliminar»; rótulo según estado (`Activar loop` / `Quitar loop`);
  deshabilitado si el archivo no tiene duración (`sourceDurationMs <= 0`, no hay nada que repetir).
- `EditorViewModel.toggleAudioLoop(clipId)` — lee el estado actual y **delega en `setAudioLoop`** (un único punto de escritura: mismo checkpoint de
  deshacer y misma regla de no-op). La API pública (`applyAudioLoop`) no cambia.
- El estiramiento por las líneas gruesas de loop del clip seleccionado, el dibujo de repeticiones, la exportación y la persistencia **no se tocaron**.

## Código retirado (quedó sin consumidores)
- `AudioModuleId.LOOP`, la fila «Loop» de `ModulesDrawerPanel`, `audioLoopWindowOpen`, `audioLoopWindowZIndex` y el bloque de la ventana en `EditorScreen.kt`.
- `AudioToggleFloatingWindow.kt` (su único usuario era Loop; «Silencio» ya había salido en la fase de mezcla en barra flotante).
- `res/drawable/ic_loop.xml` (solo lo usaban la fila y la ventana). `ic_loop_preview.xml` (bucle de vista previa de la ventana ampliada) **se conserva**.

## Pruebas
- Nuevo `ClipMenuLoopPlacementStructureTest` (estructural, JVM puro): cajón sin Loop, sin ventana ni toggle genérico, menú con el ítem y cableado hasta el ViewModel.
- Ningún test existente referenciaba `AudioModuleId.LOOP`, `AudioToggleFloatingWindow` ni `ic_loop`.

## Verificación pendiente (sin SDK/Gradle en esta sesión)
1. `./gradlew assembleDebug testDebugUnitTest`.
2. En dispositivo: Módulos → Audio ya no muestra Loop.
3. Mantener presionado un clip → «Activar loop»: aparecen las líneas gruesas de loop (clip seleccionado) y se pueden estirar; «Quitar loop» las oculta.
4. Deshacer / rehacer revierte el cambio de loop; guardar y reabrir conserva el estado; exportar repite el audio solo con loop activo.
5. Menú más alto: confirmar que no se corta en el borde inferior (el posicionado `ContextMenuPositionTest` lo acota a la pantalla).
