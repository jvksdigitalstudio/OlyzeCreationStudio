# FASE — Playhead del timeline por tramos (estilo Playlist de FL Studio Mobile)

## Problemas reportados (capturas del proyecto "Cr7")
1. La línea del playhead se dibujaba ENCIMA de los paneles de acciones de las filas.
2. La línea se cortaba a mitad de la lista y no llegaba al final de la última capa.

## Causa raíz
- El playhead era un único `Box` hermano posterior de toda la lista: por orden de dibujo
  quedaba sobre todo lo que vive dentro de las filas (incluido `RowSideActionsPanelHost`).
  Su área táctil (28dp) además bloqueaba los toques de los botones del panel bajo ella.
- El alto salía de `(ROW_HEIGHT * filas).coerceAtMost(280.dp)`: tope arbitrario en dp.

## Solución (arquitectura, sin parches)
- `PlayheadLineController` + `LocalPlayheadLine`: estado compartido estable (X como lambda,
  leída en fase de layout/dibujo → las filas no se recomponen al reproducir).
- Tramos: `PlayheadRulerHandle` (regla + divisor, con manija) y `PlayheadLaneSegment` en
  Master, capas de imagen y carriles de audio, declarado ANTES del panel de cada fila
  (audio: zIndex 2.5 entre selección 2 y panel 3). El panel lo tapa por orden de apilado.
- Línea continua: todas las filas miden 36dp; termina exactamente al final de la última capa
  y scrollea en el mismo frame que las filas.
- Separador de miniaturas: alto = cabecera + `visibleRowsHeightPx(...)` (puro, testeado).
- Acabado profesional: trazo alineado a píxel entero + halo oscuro translúcido
  (`drawPlayheadStroke`) para legibilidad sobre cualquier color de clip.

## Archivos
`TimelineView.kt`, `AudioTrackRow.kt`, `PlayheadGeometry.kt` (nuevo),
`PlayheadGeometryTest.kt` (nuevo).

## Verificación pendiente en dispositivo (no se pudo compilar en el entorno de generación)
- Compilar (`./gradlew :app:testDebugUnitTest` y `assembleDebug`).
- Abrir panel de una fila con el playhead detrás: debe quedar tapado.
- Proyecto con >8 filas: la línea llega al final de la última capa; con scroll se recorta al viewport.
- Arrastrar playhead desde regla y desde un carril; botones del panel siguen tocables.
