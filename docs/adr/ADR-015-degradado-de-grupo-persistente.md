# ADR-015 — Degradado repartido entre filas: un solo motor, color sólido guardado por fila

**Estado:** Decidido e implementado; corregido y ampliado por la auditoría del 2026-10-17 (ver "Addendum" y
`docs/AUDITORIA_COLOR_DE_FILAS_2026-10-17.md`). **Pendiente de compilar, correr los tests y verificar en dispositivo** (ver "Verificación").
**Archivos:** `engine/color/GroupGradientEngine.kt`, `engine/color/GradientGroup.kt`,
`engine/color/LegacyGradientMigration.kt`, `engine/scene/LayerColor.kt`, `engine/audio/AudioTrackColor.kt`,
`engine/scene/Layer.kt`, `engine/audio/AudioClip.kt` (`AudioTrack`), `data/ProjectModels.kt`, `data/ProjectStorage.kt`,
`data/LegacyGradientGroups.kt`, `viewmodel/EditorViewModel.kt`, `ui/TimelineView.kt`, `ui/AudioTrackRow.kt`,
`ui/AudioClipDetailHost.kt`, `ui/LayerDialogs.kt`, `ui/theme/Theme.kt`.

## Contexto
Referencia: "Color selected → Gradient" del Channel Rack de FL Studio (manual oficial): las filas seleccionadas se
colorean **en secuencia** entre un color inicial y uno final, en un espacio de color elegible. El manual no documenta la
fórmula, ni que la primera fila sea exactamente el color inicial, ni el espacio por defecto; eso se toma del
comportamiento visual de las capturas de referencia. El espacio por defecto se MIDIÓ sobre esas capturas en la
auditoría del 2026-10-17 (ver el addendum, punto 5): es RGB directo.

La implementación anterior tenía estos defectos (verificados en el código):
1. Los extremos no llegaban al color elegido: `(k + 0.5) / n` (con 2 filas, 25 % y 75 %).
2. **Dos fórmulas** para lo mismo: `androidx.compose.ui.graphics.lerp` en la UI y un promedio RGB manual en el
   ViewModel. Lo que se veía y lo que se guardaba (`customColorArgb`) difería.
3. El grupo **no se guardaba**: la UI lo deducía al dibujar (filas consecutivas con el mismo degradado). Mover, insertar
   o borrar una fila rompía el grupo y recalculaba los colores con otro `n`. Mientras `useGradientColor` fuera `true`,
   el color muestreado guardado no se leía.
4. Una sola ruta de color (sin selector de espacio).
5. Saturación distinta entre filas de grupo (color exacto) y filas sueltas (`strongVariant`).

## Decisión
1. **Motor único y puro** `GroupGradientEngine` (sin Android ni Compose), usado por UI y ViewModel:
   `t = k / (n - 1)`; primera fila = color inicial exacto, última = final exacto; resultado siempre opaco.
   Espacios: `RGB` (mezcla directa canal a canal, **por defecto** — es lo que hace FL Studio), `HUE` (ruta corta del
   matiz, colores vivos tipo arcoíris), `PERCEPTUAL` (Oklab).
2. **Cada fila guarda su color sólido** (`customColorArgb`, `useGradientColor = false`). La UI dibuja solo ese color:
   se eliminó la deducción por filas consecutivas (`labelColumnColorOverrides`, `rowBodyBrush`, `labelColumnBrush`).
3. **Metadato de edición** `gradientGroup: GradientGroupRef?` (id + espacio) por capa y carril, persistido como dos
   campos opcionales (`gradientGroupId`, `gradientGroupSpace`). Los extremos, ángulo y forma ya se guardaban. Solo sirve
   para reabrir el diálogo con el reparto original; no afecta al dibujo. Cualquier otro cambio de color de una fila
   (`withCustomColor`, `withGradient`, `withDefaultColor`) la saca del grupo.
4. **Una sola capa marcada** conserva el degradado A→B dentro de su fila, como antes.
5. **Realce de la fila** (`Theme.rowAccentColor`): una fila de grupo muestra su color exacto; el resto pasa por
   `strongVariant`, que delega en `GroupGradientEngine.strongAccent` (saturación al máximo y brillo +5 %; un color sin
   matiz —gris, blanco, negro— se conserva sin matiz). Se decidió así para no cambiar el aspecto de ningún proyecto
   existente. **Decisión abierta:** es la causa de que un mismo color se vea distinto en una fila suelta y en una de
   grupo; ver `docs/PENDIENTES.md` #5.
6. **Transformaciones de color compartidas** `Layer.with*` / `AudioTrack.with*`: el ViewModel ya no copia campos a mano.
7. **Migración única al abrir** (`LegacyGradientMigration` + `migrateLegacyGradientGroups`): reproduce la deducción
   antigua (rachas de ≥ 2 filas consecutivas con el mismo degradado; posición `(k + 0.5) / n`, mezcla Oklab) y fija esos
   tonos, de modo que el proyecto se ve igual. Es idempotente. Las capas se modifican en su sitio porque el cargador ya
   depositó el bitmap decodificado en cada `Layer` (`copy()` lo perdería).

## Addendum — auditoría 2026-10-17
Defectos encontrados al auditar el subsistema contra el código real y corregidos sin parches:
1. **Fila gris → roja.** El realce de fila (`strongVariant`) subía la saturación de TODO color; el matiz de un gris no
   está definido (vale 0), así que con el modo Negro y Blanco la miniatura y el panel de una fila suelta se veían
   rojos. Ahora lo calcula `GroupGradientEngine.strongAccent` y un color sin matiz se queda sin matiz. Además se
   eliminó el truncado `toInt()` de los canales (ahora redondea, vía `Color.toArgb`).
2. **Todavía había una segunda fórmula.** `Theme.blendColors` aplanaba un degradado propio con un promedio RGB (tonos
   apagados) mientras todo lo demás usa Oklab. Ahora usa `GroupGradientEngine.midpoint` (Oklab, t = 0,5) y conserva el
   promedio de opacidad. Con esto es cierto lo que dice la decisión 1: no hay otra fórmula de color de fila en el
   proyecto. Efecto visible: el color plano de un degradado PROPIO de una fila (bordes, acentos) cambia ligeramente.
3. **Deshacer revertía el color de los carriles de audio en silencio.** El snapshot de undo restaura `audioTracks`
   completo, pero el color de carril no tomaba checkpoint: no era deshacible y el siguiente deshacer de cualquier otra
   acción lo devolvía al valor anterior. Ahora `setAudioCustomColor/Gradient`, `resetAudioColor` y los lotes
   (`setLayersCustomColor`, `setLayersGradient`, `resetLayersColor`) toman UN checkpoint por acción.
4. **Duplicación en `Theme.kt`:** la resolución color/brush de capa y de carril estaba copiada 4 veces; ahora hay un
   único `resolveRowColor`/`resolveRowBrush`. Se quitaron dos KDocs huérfanos y `AngledLinearGradientBrush` tiene
   igualdad estructural (sin ella cada recomposición lo veía "distinto" y repintaba la fila).

5. **El espacio por defecto no coincidía con FL Studio.** El manual oficial solo dice que las filas se colorean "en
   secuencia entre el punto inicial y el final, en el espacio de color elegido": no nombra la fórmula. Se midieron los
   colores reales de tres capturas de referencia de FL y se ajustó cada modelo a los extremos de cada una:

   | Captura de FL | Filas | RGB directo | Oklab | Matiz (HSV) |
   |---------------|-------|-------------|-------|-------------|
   | magenta → menta | 23 | **3,7** (máx. 7) | 37,6 | 71,6 |
   | rojo → azul | 13 | **4,7** (máx. 11) | — | 24,9 |

   (error medio, en niveles RGB de 0 a 255). FL mezcla en RGB: sus tonos medios son grises azulados apagados, no el
   arcoíris vivo del recorrido por matiz. El motor traía `HUE` por defecto, así que el degradado de una selección nueva
   no se parecía al de FL. En la captura del propio usuario (azul → lima, 14 filas) el recorrido por matiz hace saltar
   la luminosidad de 0,07 a 0,77 en las primeras 6 filas y la deja casi plana (0,72–0,78) en las 5 siguientes: de ahí el
   "verde oscuro, verde claro, amarillo" incoherente que se veía. Ahora el defecto es `RGB`; el paso máximo de
   luminosidad entre filas baja de 0,25 a 0,14. `HUE` y `PERCEPTUAL` siguen en el selector. Los grupos YA guardados
   conservan el espacio con el que se crearon (se persiste por grupo): no se recolorea ningún proyecto existente.
   Limitación honesta: las mediciones salen de capturas JPEG, no del código de FL; el error de 4 niveles es compresión.

6. **Miniatura de capa recortada por el borde de grupo.** El borde de color que marca una capa de grupo (2,5 dp) se
   pintaba ENCIMA de la imagen, que llenaba el recuadro de 36 dp a sangre completa, y su color es el mismo que el
   fondo de la fila: parecía un recorte. Todo logo que tocara el margen de su imagen (p. ej. un aro) quedaba cortado
   en plano por los cuatro lados, con forma octogonal. Simulado a la densidad real (2,64 px/dp): el 20 % del aro
   oculto. Ahora la imagen se dibuja dentro de un margen de 3 dp (borde + 0,5 dp), por dentro del borde y de las
   esquinas redondeadas: 0 % oculto, imagen de 30 dp en el recuadro de 36 dp. La geometría sale de constantes únicas
   (`THUMBNAIL_BOX_SIZE`, `THUMBNAIL_CORNER_RADIUS`, `THUMBNAIL_GROUP_BORDER`, `THUMBNAIL_CONTENT_INSET`) y la fija
   `ThumbnailGeometryTest`. Afecta a todas las capas, no solo a las de grupo (antes las esquinas redondeadas también
   cortaban una imagen a sangre completa).

Cambio de comportamiento a tener en cuenta: un reparto NUEVO usa RGB por defecto (antes Matiz; los ya guardados no
cambian); el undo de color de audio es nuevo (antes no existía); el realce de una
fila gris cambia de rojo a gris (era un defecto); el acento de color con matiz puede variar ±1 nivel por canal respecto
a `android.graphics.Color.RGBToHSV` por usar coma flotante de doble precisión.

## Alternativas descartadas
- *Guardar solo el color sólido, sin metadato:* más simple, pero el diálogo no podría reabrir el degradado.
- *Normalizar el realce para todas las filas:* cambiaría el aspecto de todos los proyectos con colores propios.
- *Exponer `gradientGroup` en `LayerSnapshot` (API pública):* es dato interno de edición; arrastraría un tipo del motor.
- *Extraer un value object `LayerColor` común a `Layer` y `AudioTrack`:* es la dirección correcta a largo plazo
  (hoy cada campo de color se copia en ~10 estructuras de guardado y deshacer), pero toca todo el modelo y se aplaza.

## Verificación
- Tests JVM nuevos: `GroupGradientEngineTest` (incluye `strongAccent`, `midpoint` y el contrato medido contra FL, añadidos en la auditoría
  del 2026-10-17), `ThumbnailGeometryTest`, `LegacyGradientMigrationTest`, `LegacyGradientGroupsTest`, `ProjectDataGradientGroupTest`; actualizado
  `AudioTrackColorTest`. Los valores esperados se calcularon con una
  referencia independiente en Python que replica el algoritmo.
- **No ejecutados al entregar** (el entorno de desarrollo no tenía compilador de Kotlin ni Gradle): compilar y correr
  `./gradlew testDebugUnitTest` en CI antes de dar el cambio por bueno.
- Comprobación manual: aplicar un degradado a 2, 3 y 16 filas (con audio); mover, borrar e insertar filas; reabrir el
  diálogo; deshacer; guardar, cerrar y reabrir; abrir un proyecto guardado con la versión anterior.
  Tras la auditoría del 2026-10-17, además: color de un carril de audio → deshacer/rehacer (un paso por acción, también
  en lotes mixtos capas + carriles); modo Negro y Blanco en una fila suelta (debe verse gris, no roja); un deshacer
  ajeno no debe cambiar el color de ningún carril. Miniaturas: una imagen que toque su propio margen (aro, logo
  redondo) debe verse completa y con aire a los cuatro lados, en filas de grupo y sueltas. Repartir el mismo azul → lima entre 14 filas con RGB y con Matiz y
  comprobar que con RGB la luminosidad sube pareja; que un proyecto guardado antes conserve su espacio de color.
- La equivalencia exacta de la migración con `compose.ui.graphics.lerp` (Oklab) se espera idéntica o ±1 nivel por
  canal por precisión de coma flotante; no se comprobó contra Compose.
