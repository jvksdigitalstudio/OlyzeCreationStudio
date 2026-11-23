# Fase — Panel de acciones de la capa de audio alineado con el de las capas de imagen

**Alcance:** los íconos de acción del panel «al costado» de una capa de audio (renombrar, color, bloqueo de orden, eliminar) quedan en la
misma posición horizontal y a la misma altura que los de una capa de imagen. No cambian las acciones ni el bloque de mezcla (oídos, panorama,
volumen), solo su colocación.

## 1. Problema

Las acciones del audio iban más separadas que las de la capa de imagen: paso de 36 dp entre íconos (botón de 36 dp, sin separación, margen de
4 dp) frente a 31 dp en las capas de imagen (botón de 26 dp + 5 dp de separación, margen de 8 dp). A partir del segundo ícono cada uno quedaba
más a la derecha que su equivalente en la fila de arriba, y el bloque de mezcla arrancaba aún más lejos (+2 dp de margen extra), gastando ancho.

## 2. Solución: una sola fuente de medidas

`TimelineView.kt` declara las medidas del panel una vez y las usan **las dos** filas:

| Constante | Valor | Qué fija |
|---|---|---|
| `ROW_ACTIONS_PANEL_PADDING_HORIZONTAL` | 8 dp | margen lateral del panel |
| `ROW_ACTIONS_PANEL_PADDING_VERTICAL` | 6 dp | margen vertical del panel de imagen |
| `ROW_ACTIONS_PANEL_SPACING` | 5 dp | separación entre botones |
| `ROW_ACTION_BUTTON_SIZE` | 26 dp | área tocable de cada botón (también el valor por defecto de `RowActionIcon`) |

- Paso entre íconos: 26 + 5 = **31 dp** en ambas filas; primer ícono a 8 + 13 = 21 dp del borde del panel.
- **Alto:** panel de imagen 26 + 2 × 6 = 38 dp; panel de audio = control de volumen (36 dp) + 2 × 1 dp = 38 dp. Mismo alto, íconos centrados.
- El bloque de mezcla arranca **un paso** después del último ícono (el mismo espaciado de 5 dp; se eliminó el margen extra `AUDIO_PANEL_MIX_GAP`).
- Se eliminaron `AUDIO_PANEL_PADDING_HORIZONTAL`, `AUDIO_PANEL_BUTTON_SIZE`, `AUDIO_PANEL_ACTIONS_SPACING` y `AUDIO_PANEL_MIX_GAP` de `AudioTrackRow.kt`.

## 3. Consecuencia a tener en cuenta

El área tocable de las acciones del audio baja de 36 dp a 26 dp (la de las capas de imagen). Era una comodidad pedida antes; igualar la
posición exacta de los íconos con paso de 31 dp obliga a botones de 26 dp (con 36 dp se solaparían). El control de volumen conserva sus 36 dp.

## 4. Verificación pendiente en dispositivo

Sin poder compilar aquí: abrir el panel de una capa de imagen y el de una de audio una bajo la otra y comprobar que lápiz y paleta coinciden
en horizontal, que el bloque de mezcla arranca justo tras la basura, y que el alto de ambos paneles es igual. Probar también con una pestaña
inferior abierta (el panel pasa al pie y no cambia).
