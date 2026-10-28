# FASE 21 — Menú contextual del clip de audio: compacto y al costado

> Alcance: `ui/AudioTrackRow.kt` (`ClipPopupMenu`). Sin compilador en esta
> sesión: verificado por lectura y balance de llaves del bloque nuevo; la
> verificación real es `gradle testDebugUnitTest assembleDebug` (CI) y prueba
> en dispositivo.

## Problema (reportado con capturas)
Al mantener presionado un clip de audio, el menú (Copiar, Cortar, Duplicar…)
se abría como una hoja a TODO el ancho de la pantalla, ocupando media pantalla
de alto y lejos del clip.

## Causa raíz
1. Un `Popup` de Compose recibe como restricción máxima el tamaño de la
   **ventana completa**. Cada ítem usaba `fillMaxWidth()` y el contenedor no
   tenía ancho propio → el menú se estiraba al ancho de la pantalla.
2. Filas de ~42 dp (14 sp + 12 dp de padding vertical) × 11 ítems ≈ 460 dp.
3. Sin `PopupPositionProvider`: el Popup se anclaba a la esquina del padre sin
   considerar bordes de ventana ni cercanía al clip.

## Corrección (en el componente compartido, no un parche en el clip)
| Aspecto | Antes | Ahora |
|---|---|---|
| Ancho | Pantalla completa | Ítem más largo (`IntrinsicSize.Max`), 132–220 dp, texto con elipsis |
| Alto de fila | ~42 dp | 32 dp, 13 sp |
| Alto total | Sin límite | Acotado al alto de pantalla; hace scroll si no entra |
| Posición | Esquina del ancla | Al costado derecho del clip → izquierdo → debajo/arriba centrado; siempre dentro de la ventana |

La regla de posición es la función pura `computeContextMenuOffset` (recorta el
ancla a la parte visible de la ventana, de modo que un clip parcialmente fuera
de pantalla no empuja el menú fuera de la vista).

`ClipPopupMenu` es compartido: el mismo arreglo aplica al menú del carril vacío
("Pegar en el cursor / Seleccionar rango / Rejilla…") y al de la selección de
tiempo. En el carril vacío el ancla es el carril completo (no cabe un costado),
por lo que el menú se centra debajo (o arriba) del carril.

## Ajuste 2 — menú en dos niveles (referencia FL Studio Mobile)
Una lista de 11 filas seguía siendo alta. Como en FL Mobile (menú corto +
"More…"), el menú del clip ahora tiene dos niveles en la MISMA ventana
(se redimensiona y se reposiciona al costado):
- Nivel 1 (6): Copiar, Cortar, Duplicar, Dividir en el cursor, Eliminar, Más…
- Nivel 2 (6): Seleccionar clip, Unir con el siguiente, Pegar en el cursor,
  Invertir, Normalizar, Silenciar.
Mismas acciones y mismas reglas de habilitado que antes; solo cambia su
agrupación. `ClipMenuItem.keepsMenuOpen` permite que "Más…" no cierre el menú.

## Tests nuevos
`ContextMenuPositionTest` (7 casos): derecha, izquierda, límite inferior, ancla
de ancho completo (debajo / arriba), clip parcialmente fuera de pantalla e
invariante "siempre dentro de la ventana".

## Límites conocidos (NO tocados)
- El menú de carril vacío no nace en el punto exacto del toque (el ancla es el
  carril entero). Requeriría propagar el offset del `onLongPress`; fase propia.
- Filas de 32 dp (compactas, como FL Studio Mobile) quedan bajo los 48 dp de
  Material; aceptado para un editor de tablet.
- La barra de botones circulares que FL Mobile muestra sobre el clip
  (Copy/Delete/Snap/Edit/More…) NO se replicó: se adoptó solo su principio de
  menú corto + segundo nivel.
