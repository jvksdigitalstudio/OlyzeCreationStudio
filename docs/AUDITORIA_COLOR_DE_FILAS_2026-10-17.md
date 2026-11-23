# Auditoría — color y degradado de las filas del timeline (2026-10-17)

**Alcance:** todo el recorrido del color de una fila (capa o carril de audio): motor de reparto, transformaciones del
modelo, guardado y migración, ViewModel (aplicar, deshacer), resolución visual (`Theme.kt`) y siembra del diálogo.
**Referencia de diseño:** `docs/adr/ADR-015-degradado-de-grupo-persistente.md`.
**Método:** lectura del código real contra el ADR; constantes y fórmulas del motor verificadas contra la referencia
original (Oklab de Ottosson, HSV estándar); valores esperados de las pruebas calculados con una referencia independiente.
**Limitación:** el entorno de auditoría no tiene compilador de Kotlin ni Gradle. **Nada de esto se compiló ni se ejecutó.**
Debe compilarse y correr `./gradlew testDebugUnitTest` antes de darlo por bueno.

## Resultado

El diseño es sólido: un motor puro y único, cada fila guarda su color sólido, el grupo es solo metadato, y la
persistencia y la migración de proyectos antiguos son coherentes (ids y espacio de color se guardan y se leen con
tolerancia). No se encontraron errores en las matemáticas del motor. Sí se encontraron seis defectos reales, todos
corregidos; el quinto sale de medir las capturas de FL Studio (ver "Comparación con FL Studio") y el sexto de una
captura del usuario con la miniatura recortada.

## Defectos corregidos

| # | Gravedad | Dónde | Defecto | Corrección |
|---|----------|-------|---------|------------|
| 1 | Alta | `Theme.strongVariant` | Subía la saturación de **todo** color. Un gris (blanco, negro, modo Negro y Blanco) no tiene matiz (vale 0), así que la miniatura y el panel de una fila suelta se veían **rojos**. Además truncaba los canales (`toInt()`) en vez de redondear. | `GroupGradientEngine.strongAccent` (puro, con pruebas): un color sin matiz se conserva sin matiz; el redondeo lo hace `Color.toArgb`. |
| 2 | Alta | `EditorViewModel` / undo | El snapshot de undo restaura `audioTracks` completo, pero el color de carril no tomaba checkpoint. No se podía deshacer, y **cualquier otro deshacer devolvía el color del carril a su valor anterior en silencio**. | `replaceAudioTracks(recordUndo = true)` en el color individual; **un solo** checkpoint por acción en los lotes mixtos (capas + carriles), para no apilar dos pasos. |
| 3 | Media | `Theme.blendColors` | Seguía existiendo una segunda fórmula de color (promedio RGB, tonos apagados) que contradecía la decisión 1 del ADR ("un solo motor"). | Ahora usa `GroupGradientEngine.midpoint` (Oklab, t = 0,5) y conserva el promedio de opacidad. |
| 4 | Baja | `Theme.kt` | La resolución de color/brush de capa y de carril estaba copiada cuatro veces; dos KDocs huérfanos apilados; `AngledLinearGradientBrush` sin igualdad, así que cada recomposición lo trataba como distinto y repintaba. | `resolveRowColor`/`resolveRowBrush` únicos; KDocs limpiados; `equals`/`hashCode` estructurales. |
| 5 | Alta | `GradientColorSpace.DEFAULT` | El espacio por defecto era `HUE` (arcoíris), pero FL Studio mezcla en **RGB directo**. En la captura del usuario (azul → lima) la luminosidad salta y se queda plana: el "verde oscuro, verde claro, amarillo" incoherente. | `DEFAULT = RGB`; Matiz y Perceptual siguen en el selector; los grupos ya guardados no cambian. |
| 6 | Alta | `TimelineView` (miniatura de capa) | El borde de color de las capas de grupo (2,5 dp) se pintaba encima de la imagen, que llenaba los 36 dp a sangre completa, con el mismo color que la fila: un aro quedaba **cortado en plano por los cuatro lados** (20 % oculto, medido en simulación a la densidad real). Las esquinas redondeadas también cortaban imágenes a sangre completa. | La imagen se dibuja con un margen de 3 dp (`THUMBNAIL_CONTENT_INSET` = borde + 0,5 dp): queda por dentro del borde y de las esquinas, 0 % oculto. Geometría en constantes únicas y fijada por `ThumbnailGeometryTest`. |

Archivos modificados: `engine/color/GroupGradientEngine.kt`, `ui/theme/Theme.kt`, `viewmodel/EditorViewModel.kt`,
`api/audio/AudioApiImpl.kt` (solo un comentario falso sobre el undo del audio), `engine/color/GradientGroup.kt` (valor por
defecto), `ui/LayerDialogs.kt` (orden y texto del selector), `ui/TimelineView.kt` (geometría de la miniatura),
`GroupGradientEngineTest.kt` (9 pruebas nuevas) y `ThumbnailGeometryTest.kt` (3 pruebas nuevas).

## Comparación con FL Studio (medida, no supuesta)

El manual oficial solo dice que las filas se colorean "en secuencia entre el punto inicial y el final, en el espacio de
color elegido"; no da la fórmula. Se midieron los colores reales de las capturas de referencia y se ajustó cada modelo a
los extremos de cada una (error medio en niveles RGB, 0–255):

| Captura de FL | Filas | RGB directo | Oklab | Matiz |
|---------------|-------|-------------|-------|-------|
| magenta → menta | 23 | **3,7** | 37,6 | 71,6 |
| rojo → azul | 13 | **4,7** | — | 24,9 |

FL mezcla en RGB: sus tonos medios son grises azulados apagados y su luminosidad avanza pareja. En la captura del usuario,
con el recorrido por matiz la luminosidad pasa de 0,07 a 0,77 en 6 filas y queda plana en las siguientes 5; con RGB el
paso máximo entre filas baja de 0,25 a 0,14. Limitación: son capturas JPEG, no código de FL (4 niveles de error es
compresión). Las otras dos capturas (una borrosa, con una tercera paleta) no se usaron para ajustar nada.

## Cambios de comportamiento visibles

- La miniatura de cada capa se ve ENTERA: la imagen ocupa 30 dp dentro del recuadro de 36 dp (antes 36 dp con el borde
  tapándole 2,5 dp por lado). Las imágenes que ya tenían aire propio se ven ligeramente más chicas (30 en vez de 36 dp).
- Un reparto multicapa NUEVO sale en RGB por defecto (antes Matiz): se parece al de FL, con el tono medio más apagado.
  Quien prefiera el arcoíris lo elige en "Mezcla: Matiz". Los grupos ya guardados no cambian.

- Una fila suelta en modo Negro y Blanco ahora se ve gris (antes, roja).
- El color plano de un degradado **propio** de una fila (bordes, acentos) cambia ligeramente al pasar de promedio RGB a Oklab.
- El color de carril de audio ahora es deshacible y rehacible.
- El acento de un color con matiz puede variar ±1 nivel por canal respecto al cálculo anterior (coma flotante).

## Hallazgos que NO se tocaron (decisiones abiertas, en `docs/PENDIENTES.md`)

- **#4** Nombre, grilla y candado de orden de un carril de audio tienen el mismo defecto que el color (#2).
- **#5** Una fila suelta se realza (saturación máxima) y una de grupo muestra su color exacto: un mismo color se ve
  distinto. Es lo más probable detrás de "veo cosas raras al cambiar el color" y es decisión de producto (cambia el
  aspecto de proyectos existentes).
- **#6** Un degradado repartido entre varias filas descarta la opacidad elegida en el diálogo.
- **#3 (ya existente)** Los ocho campos de color se copian a mano en unas diez estructuras: la deuda estructural
  principal del subsistema; conviene hacerla antes de añadir otro campo de color.

## Riesgos que no pude verificar sin compilar

- Las llamadas `replaceAudioTracks(ids) { ... }` siguen siendo válidas porque `recordUndo` tiene valor por defecto y la
  lambda sigue siendo el último parámetro; el compilador lo confirmará.
- Las pruebas nuevas usan valores calculados fuera de Kotlin (gris 0x808080 → 0x868686; 0x804040 → 0x860000; punto
  medio negro/blanco → 0x636363). Si alguna difiere en un nivel, es redondeo de coma flotante, no un defecto.

## Verificación pendiente en dispositivo

Miniatura de capa con un logo redondo o a sangre completa (`1.png`/`2.png`): aro entero y con aire a los cuatro lados,
en filas de grupo y sueltas, seleccionadas o no; color de un carril → deshacer/rehacer; deshacer una acción ajena sin que cambie ningún color de carril; fila suelta en
Negro y Blanco; degradado a 2, 3 y 16 filas con audio; guardar, cerrar y reabrir; abrir un proyecto de la versión anterior.
