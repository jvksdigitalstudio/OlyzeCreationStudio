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
2. Íconos con significado (eliminar, candado de canvas activo, candado de orden activo): mismo matiz, aclarados u
   oscurecidos lo mínimo hasta 3:1 (`ensureContrast`, en Oklab hacia blanco/tinte, gana la dirección de menor cambio).
3. Degradados: se evalúan extremos, punto medio y la variante realzada que usa el acordeón del pie.
4. Dibujos con colores propios (`ActionTone.Original`: paleta, ojo tachado): tal cual sobre oscuro; sobre claro se
   invierten sus tonos con un `ColorFilter` (blanco → tinte, detalle oscuro → claro).
5. Controles de volumen/panorama: usan `content` como color.

`LayerAction.tint: Color` pasó a `tone: ActionTone` (rol, no color), y `RowActionIcon` recibe la paleta: una sola
fuente de verdad en las capas de imagen, la pista de audio y el acordeón.

## No-cambios
Colores de fila, panel y borde; geometría y tamaños; comportamiento de toque. Las insignias de candado de la miniatura
(sobre chip oscuro propio) no cambian.

## Verificación
- `RowIconContrastTest`: referencias WCAG, rosa conserva blanco, claros pasan a tinte oscuro, "Eliminar" legible en
  oscuro/rosa/cian y barrido de 216 fondos con contraste ≥ 3:1 en todos los íconos.
- En dispositivo: probar capas cian, amarilla, verde croma, rosa, blanco y negro, con degradado, y capa oculta.
