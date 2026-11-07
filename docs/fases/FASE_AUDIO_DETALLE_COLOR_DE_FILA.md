# Mejora: la onda de la ventana ampliada del audio respeta el color de su fila

## Problema
La señal en la ventana ampliada se veía en otro color que la fila/carril del clip.

## Causa raíz
`AudioClipDetailHost` recalculaba el color por su cuenta con solo el color propio del carril
(`customColorArgb` / degradado) y no consideraba el tono repartido de "Multicolor"
(`labelColumnColorOverride`), que SÍ usa la fila (`AudioTrackRow.trackColorStrong`). Además duplicaba
el literal del color de fábrica y la mezcla con blanco (0.55) en dos sitios.

## Solución
- `Theme.kt`: `audioSignalColor(trackColorStrong)` + `AUDIO_SIGNAL_WHITE_MIX` = única definición del color de la señal.
- `AudioTrackRow`: usa `audioSignalColor`; `AUDIO_TRACK_COLOR` pasa a `internal`.
- `AudioClipDetailHost`: nuevo parámetro `trackColorOverrides`; color = `override ?: color propio`, igual que la fila.
- `TimelineView`: pasa `labelColumnColorOverrides` (sin variables locales nuevas, por el límite de registros / VerifyError).

Solo cambia el color de la señal; el fondo negro y el resto de la ventana no se tocaron.
