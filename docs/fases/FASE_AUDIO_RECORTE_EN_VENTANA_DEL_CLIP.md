> **Actualización 2026-10-18:** la restricción de «un único rango» descrita abajo fue reemplazada: ver `FASE_AUDIO_RECORTE_MULTIRANGO.md`.

# Fase — Recorte pasa del cajón «Módulos» a la ventana ampliada del audio (2026-10-18)

## Cambio
| Antes | Ahora |
|-------|-------|
| Módulos → Audio → **Recorte** abría una ventana flotante con un deslizador («Inicio del recorte: N s», `setAudioTrimStart`). | **Recorte** se retiró del cajón. En la ventana ampliada del audio (pantalla «Cr7») hay un botón **Recorte**, justo a la izquierda de **Reversa** (orden: Cargar … Recorte · Reversa · Repetir · ✕). |

Con esto el cajón «Módulos» ya no tiene la categoría «Audio»: Loop, Fade, Volumen, Pan, Silencio y ahora Recorte salieron de allí (ver las fases anteriores).

## Comportamiento del botón
- Recorta el clip **al rango que el usuario seleccionó arrastrando sobre la onda** (selección ya existente: `FASE_AUDIO_DETALLE_SELECCION_DE_RANGO.md`).
- Sin selección el botón se ve atenuado (`alpha 0.4`) y no responde (`enabled = false`, también para accesibilidad).
- No destructivo: el archivo fuente no se toca. Cambian `timelineStartMs`, `clipLengthMs` y `trimStartMs` del clip, de modo que **cada instante del audio suena exactamente en el mismo lugar del proyecto** que antes del recorte.
- El clip nunca crece → no puede invadir a un clip vecino.
- Fundidos: el fade-in se conserva solo si el inicio del clip no cambió; el fade-out solo si el final no cambió (misma regla que `slicePiece`, el corte de clips). Un corte nunca deja un fundido a mitad de la señal.
- Con Reversa activa la selección y `trimStartMs` están sobre el mismo eje (el archivo invertido), por lo que no hay conversión.
- Con loop, el clip es más largo que el archivo: solo se recorta el **primer pase** audible (las repeticiones no tienen posición propia en el archivo).
- Una selección que sobresale se acota a lo que suena; una selección fuera de lo audible no hace nada (ni toca el historial de deshacer).
- **Largo mínimo:** un clip nunca baja de `MIN_AUDIO_CLIP_LENGTH_MS` (100 ms, regla de todo el proyecto, igual que al estirar o dividir). El botón solo se habilita con una selección de al menos ese largo, y si lo que suena dentro de ella es menor la regla no aplica nada. `trimAudioClipToSelection` devuelve `Boolean`: la ventana descarta la selección **solo si el recorte se aplicó**.
- Tras aplicar el recorte la selección se descarta (`AudioClipDetailSelectionState.clear()`) y el editor deja de acotar la vista previa a ella.
- Un solo paso de deshacer por recorte.

## Diseño (código real, archivos separados)
- **Regla pura** `trimClipToSelection` / `ClipTrim` — `engine/audio/AudioClipTrimToSelection.kt` (sin Android ni Compose, probada con JUnit).
- **ViewModel** `EditorViewModel.trimAudioClipToSelection(clipId, selection)` — aplica la regla en un único punto de escritura (`replaceAudioClip`).
- **Acción** `AudioClipEditActions.onTrimToSelection` (`AudioTrackRow.kt`), cableada en `EditorScreen.kt` junto a `onToggleReverse`; una sola construcción de las acciones sirve al menú del clip y a la ventana.
- **UI** `AudioClipDetailHeader.kt` (`DetailTrimButton`, reusa `DetailActionTile`) y `AudioClipDetailPanel.kt` (`hasSelection` vía `derivedStateOf`: la ventana solo recompone cuando aparece o desaparece una selección, no en cada movimiento del dedo).
- **Ícono premium multicolor** `res/drawable/ic_trim_premium.xml`: conversión 1:1 del SVG entregado (viewBox 80×80; el rectángulo girado `matrix(0.7071 -0.7071 0.7071 0.7071 …)` quedó precalculado como polígono). Se dibuja con `Image` (sin tinte: un tinte lo aplanaría), por eso `DetailActionTile` acepta `iconTint = null`.
- **Textos** `audio_detail_trim` y `audio_detail_trim_desc` en `values/` y `values-en/` (nada de texto fijo en la UI de la cabecera).

## Código retirado (quedó sin consumidores — sin duplicados ni código muerto)
- `AudioModuleId` (enum), `onAudioModuleClick` y `hasAudioClip` de `EditorBottomBar`/`ModulesDrawerPanel`, la categoría «Audio» y la fila «Recorte».
- En `EditorScreen.kt`: `audioTrimWindowOpen`, `audioTrimWindowZIndex`, `onOpenAudioModule`, `audioModuleTargetClip`, el import de `moduleTargetClip` y el bloque de la ventana flotante.
- `ui/AudioSliderFloatingWindow.kt` (su único usuario era Recorte) y `res/drawable/ic_trim.xml`.
- `AudioTrack.moduleTargetClip(playheadMs)`, `List<AudioClip>.moduleTargetClipAt`, `AudioTrack.moduleTargetClip(playheadMs, selectedClipId)` (`AudioClip.kt`) y `List<AudioTrack>.moduleTargetClip(...)` (`AudioTrackList.kt`): solo las usaban los módulos flotantes de audio. Las menciones en fases/ADR anteriores son históricas.
- Comentario del contador `floatingWindowZOrderCounter` corregido (nueve ventanas, valor inicial máximo 8f).

## Lo que NO se tocó (a propósito)
- `EditorViewModel.setAudioTrimStart` y `applyAudioTrimStart` / `AudioApi`: son API pública del motor (extensiones y pruebas), no del cajón.
- Fundidos, loop, estiramiento por líneas, exportación, persistencia, regla de tiempo y cabezal de la ventana.

## Pruebas
- Nuevo `AudioClipTrimToSelectionTest` (regla pura): largo mínimo, tramo interior, invariante «cada instante suena donde sonaba», acotado a lo audible, fundidos por borde, selección fuera de lo audible, loop, nunca más largo que el clip, redondeo, entradas vacías.
- Nuevo `ClipTrimPlacementStructureTest` (estructural, JVM puro): cajón sin Recorte, sin ventana/enum/archivos huérfanos, botón antes de Reversa y sin tinte, cableado hasta el ViewModel.
- Los tests estructurales de Loop y Fade siguen válidos (afirman ausencia).

## Verificación pendiente (sin SDK/Gradle en esta sesión)
1. `./gradlew assembleDebug testDebugUnitTest`.
2. En dispositivo (Cr7): Módulos ya no muestra «Audio».
3. Abrir la ventana del audio: «Recorte» a la izquierda de «Reversa», atenuado sin selección.
4. Arrastrar sobre la onda para seleccionar → «Recorte»: el clip queda con ese tramo, en el mismo punto sonoro; la selección desaparece; Deshacer lo revierte.
5. Probar con Reversa activa y con un clip estirado en loop.

## Auditoría posterior (misma fecha)
Revisión completa de lo implementado contra el código real. Hallazgo corregido: el recorte permitía clips menores al largo mínimo del proyecto y la ventana descartaba la selección aunque no se hubiera aplicado nada (ver «Largo mínimo»). Verificado sin cambios: ningún test existente depende de lo retirado (los estructurales de Loop/Fade solo afirman ausencia), no quedan referencias a `AudioModuleId`/`moduleTargetClip`/`AudioSliderFloatingWindow`/`ic_trim`, y el clip solo se encoge, así que no puede invadir a un vecino.
