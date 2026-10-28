# ADR — Contraste automático de los íconos del panel de opciones de la fila

- **Fecha:** 2026-10-17
- **Estado:** aplicado (pendiente de verificación en dispositivo)
- **Archivos:** `ui/theme/RowIconContrast.kt` (nuevo), `ui/theme/Theme.kt` (`layerPanelBackgrounds`,
  `audioPanelBackgrounds`), `ui/RowIconToneFilter.kt` (nuevo), `ui/TimelineView.kt`, `ui/AudioTrackRow.kt`

## Problema
El panel de opciones toma el color (o degradado) de identidad de la fila, que el usuario elige libremente. Los
íconos tenían colores FIJOS (blanco 85 %, rojo `#FF6B6B`, ámbar, celeste): sobre cian/amarillo claro el blanco y los
controles de audio se perdían; sobre rosa/magenta el rojo de "Eliminar" desaparecía.

## Decisión
No se tocan los colores de capa ni de ventana. Los íconos se calculan contra el fondo REAL del panel (WCAG 2.x):
1. Base (`content`): blanco mientras alcance 3:1 en el peor muestreo del fondo; si no, tinte oscuro `#1A1233`
   (si lo supera). Sobre cualquier color uno de los dos da ≥ 4,58:1. El rosa fuerte conserva el blanco.
2. Eliminar: conserva su rojo mientras logre 4,5:1 (nivel de texto de WCAG; el trazo de 15 dp es fino y 3:1 queda al
   límite); si no, se aclara/oscurece lo mínimo (`ensureContrast`, Oklab hacia blanco/tinte, gana la dirección de menor
   cambio) y, si ningún rojo llega (fondos rojos/rosas de tono medio), usa el color base: legible antes que tono sobre tono.
2b. Candados ACTIVOS (canvas y orden): patrón "interruptor encendido": chip circular relleno con el color del estado
   (≥ 3:1 contra el panel) y trazo blanco u oscuro según lo que mejor lea sobre el chip (≥ 4,5:1). El estado se distingue
   por la FORMA (chip lleno vs. ícono liso), no solo por el tono, así que se ve igual sobre cualquier color de capa.
3. Degradados: se evalúan extremos, punto medio y la variante realzada que usa el acordeón del pie.
4. Dibujos con colores propios (`ActionTone.Original`: paleta, ojo tachado): tal cual sobre oscuro; sobre claro se
   invierten sus tonos con un `ColorFilter` (blanco → tinte, detalle oscuro → claro).
5. Controles de volumen/panorama: usan `content` como color.

`LayerAction.tint: Color` pasó a `tone: ActionTone` (rol, no color); `RowActionIcon` recibe la paleta y dibuja el chip
de los candados activos: una sola
fuente de verdad en las capas de imagen, la pista de audio y el acordeón.

## No-cambios
Colores de fila, panel y borde; geometría y tamaños; comportamiento de toque. Las insignias de candado de la miniatura
(sobre chip oscuro propio) no cambian.

## Verificación
- `RowIconContrastTest`: referencias WCAG, rosa conserva blanco, claros pasan a tinte oscuro, "Eliminar" a 4,5:1 o color
  base, chips y trazos de candados activos legibles, y barrido de 216 fondos con contraste ≥ 3:1 en todos los íconos.
- En dispositivo: probar capas cian, amarilla, verde croma, rosa, blanco y negro, con degradado, y capa oculta.
