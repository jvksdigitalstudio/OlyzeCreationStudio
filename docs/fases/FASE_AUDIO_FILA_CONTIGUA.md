# FASE — Fila de audio contigua (sin brecha superior)

**Fecha:** 2026-10-16 · **Archivos:** `ui/AudioTrackRow.kt`, `ui/PlayheadContinuityStructureTest.kt`

## Síntoma (captura real del usuario)
Sobre cada carril de audio del timeline se ve una línea oscura / brecha que las filas de capa de imagen no tienen:
la fila de audio no queda "pegada" a la anterior como el resto de la playlist.

## Causa raíz
La fila de audio era la única que añadía un separador propio en su borde superior: primero un `HorizontalDivider`
hermano (1 dp extra fuera del carril, que además cortaba el playhead) y, tras esa corrección, un filete `drawBehind`
(`AUDIO_ROW_TOP_HAIRLINE_*`). Las filas de capa ([TimelineRow]) no dibujan ninguno, por eso el audio se leía separado.

## Solución (código real, sin parche)
- Eliminados el filete `drawBehind`, sus dos constantes y los imports que quedaron sin uso (`drawBehind`, `Size`).
- La fila de audio ahora se construye igual que la de capa: `Row` de `AUDIO_TRACK_ROW_HEIGHT` (= `ROW_HEIGHT` = 36 dp),
  fondo de la pista al 30 %, sin bordes ni hermanos. Filas contiguas, grilla uniforme.
- `PlayheadContinuityStructureTest`: se invierte la regla (ya no se exige filete; se prohíbe) y se añade un test que
  garantiza que `AUDIO_TRACK_ROW_HEIGHT == ROW_HEIGHT`, condición para que las filas queden pegadas.

## Verificación
- Medición de píxeles sobre la captura: línea de ~2 px (≈1 dp a 1,78 px/dp) solo en los límites de las 3 filas de
  audio; los límites entre filas de capa de imagen no la presentan.
- Pendiente de confirmación en dispositivo (compilación vía GitHub Actions).
