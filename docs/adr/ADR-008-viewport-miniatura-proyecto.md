# ADR-008 — Miniatura de "Mis proyectos" en la proporción real del canvas (fix del verde asomando en la tarjeta de proyecto)

**Estado:** Decidido e implementado.
**Referencia:** ADR-006 (fondo ajustado al lienzo), ADR-007 (compensación
de profundidad en `LayerDrawer`) — bug reportado por el cliente sobre el
mismo proyecto de prueba (IMAX 1920×1342), esta vez visto en la tarjeta
de "Mis proyectos", no dentro del editor.

## Contexto

Después de ADR-007, el cliente confirmó que el canvas del EDITOR ya
llena por completo sin verde (ver captura de la sesión: el fondo ocupa
todo el cuadro en reposo). Pero reportó el mismo síntoma —franjas
verdes— en un lugar distinto: la miniatura/tarjeta del proyecto en la
pantalla "Mis proyectos", mostrando el fondo "flotando" en el medio con
barras verdes arriba y abajo.

Esto NO es el mismo bug que ADR-007 arregló. Es un problema
independiente, en otra capa del sistema:

`ProjectStorage.kt` genera esa miniatura llamando a
`ThumbnailRenderer.render(appContext, liveLayersForThumbnail, timeMs =
playheadMs)` — SIN pasar `widthPx`/`heightPx`, así que siempre usaba el
default de la función: `360×640` (proporción vertical, ~9:16).
`ThumbnailRenderer.render` internamente usa el mismo `LayerDrawer.drawLayer`
del preview en vivo y el export (por diseño, para que la miniatura sea
fiel al resultado real) — y `drawLayer` calcula su encuadre
(`fitScaleX`/`fitScaleY`) en modo "contain" contra la proporción del
viewport que se le pasa. Para un proyecto vertical, 360×640 es
razonable. Para un proyecto IMAX (1.43:1, horizontal), ese viewport NO
tiene ninguna relación con la forma real del canvas — el fondo (que sí
está perfectamente ajustado a 1.43:1 por `BackgroundAdjustDialog`, ver
ADR-006) queda "contenido" dentro de un marco vertical que no le
corresponde, dejando franjas vacías arriba/abajo que el motor limpia con
el mismo verde chroma-key de siempre.

Confirmación de que el fix de ADR-007 sí sigue funcionando acá: una vez
corregido el viewport (este ADR), la miniatura hereda automáticamente la
compensación de profundidad de `LayerDrawer` sin tocar ese código de
nuevo — los dos fixes son complementarios, no se pisan.

## Decisión

`ProjectStorage.kt` calcula ahora el tamaño de la miniatura a partir de
`canvasSpec.widthPx`/`canvasSpec.heightPx` (el Canvas real del proyecto,
ya resuelto en ese mismo punto de la función) en vez de dejar que
`ThumbnailRenderer.render` caiga en su default fijo:

```kotlin
val thumbnailMaxDimensionPx = 640
val thumbnailWidthPx: Int
val thumbnailHeightPx: Int
if (canvasSpec.widthPx >= canvasSpec.heightPx) {
    thumbnailWidthPx = thumbnailMaxDimensionPx
    thumbnailHeightPx = (thumbnailMaxDimensionPx.toFloat() * canvasSpec.heightPx / canvasSpec.widthPx)
        .toInt().coerceAtLeast(1)
} else {
    thumbnailHeightPx = thumbnailMaxDimensionPx
    thumbnailWidthPx = (thumbnailMaxDimensionPx.toFloat() * canvasSpec.widthPx / canvasSpec.heightPx)
        .toInt().coerceAtLeast(1)
}
```

`640px` de lado más largo es solo presupuesto de memoria/disco para una
miniatura de lista (no cambia el tamaño real del proyecto, que sigue
viviendo en `canvasSpec` sin tocar). Con el viewport ya en la proporción
correcta, el fondo llena el cuadro completo — mismo criterio que ya
funciona dentro del editor.

## Por qué esto no lo tapa `ContentScale.Crop` de la tarjeta

`ProjectsScreen.kt` ya muestra la miniatura con `ContentScale.Crop`
dentro de la tarjeta de proporción fija (`PROJECT_CARD_ASPECT_RATIO =
9/14`). Eso recorta el SOBRANTE de una imagen más grande que el marco,
pero no puede "rellenar" un hueco que ya viene pintado verde DENTRO del
bitmap guardado — el recorte actúa sobre lo que ya está en el archivo,
no puede inventar contenido donde el bitmap fuente tiene franjas vacías.
Por eso el fix tenía que ir en el ORIGEN (el tamaño con que se genera el
bitmap), no en cómo se muestra después.

## Alcance del fix

Un solo punto de cambio: el call site de `ThumbnailRenderer.render` en
`ProjectStorage.kt` (dentro de la función de guardado de proyecto). No
se tocó `ThumbnailRenderer.kt` (su firma con `widthPx`/`heightPx`
parametrizables ya existía, solo no se estaba usando desde acá) ni
`LayerDrawer.kt` (el fix de ADR-007 sigue intacto y ahora también
beneficia a este camino).

## Verificación pendiente en dispositivo real

Mismo estado que ADR-007: cambio hecho sobre el código fuente sin
Android SDK/Gradle disponible en esta sesión para compilar y correr.
Antes de cerrar:

1. Compilar y confirmar que `ProjectStorage` sigue guardando/cargando
   proyectos sin regresión (tests existentes de `ProjectStorage`).
2. Guardar un proyecto en varios formatos (vertical, cuadrado, IMAX,
   Cinemascope) y confirmar visualmente que ninguna miniatura en "Mis
   proyectos" muestra verde.
3. Confirmar que el archivo de miniatura en disco (`thumbnailFile`,
   JPEG calidad 85) no creció de forma notoria en tamaño para proyectos
   verticales (que antes ya usaban 360×640 — ahí el cambio debería ser
   nulo o mínimo).
