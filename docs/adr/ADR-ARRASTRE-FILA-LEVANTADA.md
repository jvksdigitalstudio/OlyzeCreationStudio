# ADR — Apariencia de la fila al reordenar (fila "levantada")

- **Fecha:** 2026-10-17
- **Estado:** aplicado
- **Archivos:** `ui/LayerRowDragStyle.kt` (nuevo), `ui/TimelineView.kt` (`TimelineRow`), `ui/AudioTrackRow.kt`

## Problema
Al mantener presionada la miniatura y arrastrar, la fila se veía transparente/apagada. Causa real: el modificador de
arrastre pintaba `SurfaceTintedElevated` (opaco, gris-azulado) ENCIMA del lavado del color de la capa, que quedaba tapado.
La fila perdía su identidad de color y parecía un hueco frente a las vecinas coloreadas. El bloque estaba duplicado,
copia literal, en `TimelineRow` y `AudioTrackRow`.

## Decisión
Estilo único `Modifier.layerRowSurface(trackBrush, isDragging)`, usado por ambas filas. En reposo: lavado al 30 %
(sin cambios). Arrastrando, de abajo hacia arriba: sombra de elevación (10 dp) + zIndex 10, base opaca
`SurfaceTintedDark`, color de la capa al 60 %, velo blanco 8 % y filetes blancos arriba/abajo al 55 %.

## Hueco destino
Al correrse las vecinas, el lugar libre se veía como un agujero del fondo morado. `Modifier.dragTargetSlot` dibuja
detrás de las filas un recuadro tenue (blanco 10 % + filetes 35 %) en `(índice + pasos) * alto de fila`
(`dragTargetSlotTopPx`, pura y testeada). Va tras `verticalScroll` en la `Column` de la lista, en coordenadas del
contenido; usa los mismos `dragTargetSteps` que mueven las filas, congelados durante el asentamiento, y desaparece
al terminar el arrastre.

## Indicador de reordenar
Mientras se arrastra, la columna izquierda de la fila muestra dos flechas arriba/abajo (`ic_reorder_arrows.xml`,
basado en el ícono "arrows-up-down") pegadas a su borde derecho y centradas en vertical, sobre un chip oscuro tenue.
`Modifier.reorderArrowsBadge(isDragging)` lo dibuja en el mismo nodo (sin tocar el layout); en reposo no dibuja nada.
Va antes del `.padding` de la columna para medirse contra su ancho completo (72 dp: queda libre a la derecha de la
miniatura de 36 dp).

## Alcance y no-cambios
- No toca la lógica de arrastre, el asentamiento animado ni el reordenamiento real (`finishReorderDrag`).
- Los filetes se dibujan desde `LayerRowDragStyle.kt`: `PlayheadContinuityStructureTest` prohíbe `.drawBehind {` en
  `AudioTrackRow.kt` para no cortar el playhead; en reposo no se dibuja ningún filete.

## Verificación
- `LayerRowDragStyleTest` (contrato de constantes y de uso único del estilo).
- En dispositivo: mantener y arrastrar una capa de imagen y una pista de audio; la fila debe verse sólida, con su color
  más intenso y borde claro, sin transparentarse, y asentarse sin saltos al soltar.
