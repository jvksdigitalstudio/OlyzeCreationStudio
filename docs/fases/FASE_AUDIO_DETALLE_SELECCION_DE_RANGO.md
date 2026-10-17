# Fase — Selección de rango en la ventana ampliada del audio

**Alcance:** seleccionar un rango arrastrando sobre la onda, como en WavePad, y **reproducir solo ese rango** (ver «Reproducir la
selección»). No hay acciones de edición sobre la selección (cortar, copiar, recortar…), ni lectura numérica, ni «seleccionar todo / hasta el
inicio / hasta el final»: son fases aparte.

## 1. Referencia: documentación oficial de WavePad (NCH Software)

Fuente: guía de usuario oficial de WavePad para Android (`help.nchsoftware.com/help/en/wavepad/android/help.pdf`), versión 20.xx.

| Documentación oficial | Qué se tomó |
|---|---|
| *Main Screen Reference*: la ventana superior muestra el archivo completo y al tocarla se salta; **la inferior muestra la onda según el zoom, y «tocar y arrastrar la onda crea una selección»**. | Arrastrar con un dedo sobre la onda ampliada crea la selección. |
| *Effects and Audio Processes*: «para marcar la región seleccionada puedes tocar y mantener sobre la ventana de la onda y arrastrar». | Mismo gesto: un dedo, sin modos ni botones. |
| *WavePad Basics* — «Selecting Regions»: «para seleccionar en cualquier parte, toca la ventana de la onda y arrastra sobre la parte que quieres seleccionar». También «puedes mover la posición tocando la ventana de la onda». | Un toque simple (sin arrastrar) **no deja selección**: solo sitúa la posición. Aquí el cursor sigue gobernándose desde la regla, así que el toque solo deselecciona. |
| *Zoom*: «también puedes acercar o alejar con un gesto de pellizco». | Pellizco = zoom (ya existía). Con un dedo ocupado seleccionando, **desplazar la vista pasa a ser con dos dedos**. |
| *Effects*: la región seleccionada se resalta; lo no seleccionado queda como fondo. | Relleno claro sobre lo seleccionado + una línea en cada borde. |

**Lo que la guía de Android NO documenta** y se tomó de tus capturas de WavePad: el ajuste de cada borde arrastrándolo
(en las capturas la selección pasa de 0:00.883–0:02.579 a 0:01.283–0:02.655 moviendo los bordes) y la apariencia exacta
(zona seleccionada gris claro, el resto oscuro, línea en cada borde). Esas dos cosas están implementadas según lo que
muestran las capturas, no según texto oficial.

## 2. Comportamiento implementado

- **Arrastre con un dedo:** selecciona del punto donde se apoyó el dedo (no donde cruzó el umbral de arrastre) al punto
  actual; también hacia la izquierda; se acota a `0 … duración del archivo`.
- **Arrastre que empieza sobre un borde** (a ≤ 22 dp): mueve ese borde; el opuesto queda fijo y el borde no salta (se
  conserva el punto por el que se agarró). Pasar el borde opuesto da la vuelta a la selección.
- **Toque simple:** deselecciona. **Doble toque:** sigue siendo el zoom y restituye la selección que borró su primer toque.
- **Dos dedos:** pellizco (zoom de tiempo / amplitud) y desplazamiento de la vista, con inercia, como antes.
  Si llega un segundo dedo en pleno arrastre, la selección queda como estaba.
- La selección se mide sobre el **archivo** (ms continuos), igual que la regla y el cabezal: sobrevive al zoom y al
  desplazamiento. Es estado de sesión de la ventana: no se guarda, no entra al undo/redo y no toca el audio.
- La selección también se ve **sobre la regla** (banda tenue; las líneas de borde arrancan en seco en el filo inferior de la regla, sin cruzar sus marcas), como en los editores profesionales: se lee el rango contra el tiempo. La capa es translúcida y va bajo el cabezal.
- Pulsar **REV** (o cambiar de clip) descarta la selección: la onda se invierte y el rango señalaría otro fragmento.
- El cursor del proyecto y el cabezal **no se tocan**: siguen gobernados solo por la regla.

## 3. Código

| Archivo | Responsabilidad |
|---|---|
| `AudioClipDetailSelectionMath.kt` | Matemática pura de la selección (rango de un arrastre, agarre de bordes). La conversión X↔tiempo NO está aquí: es la única del eje, en `AudioClipDetailTimeAxisMath.kt` (`detailXToSourceMs` / `detailSourceMsToX`). |
| `AudioClipDetailSelection.kt` | `AudioClipDetailSelectionState` (estado + arrastre/ajuste/toque) y `AudioClipDetailSelectionLayer` (dibujo). |
| `AudioClipDetailGestures.kt` | Un dedo → selección; dos dedos → transformación. |
| `AudioClipDetailPanel.kt` | Conexión: estado, toque/doble toque, gestos y capa (bajo la regla). |
| `AudioClipDetailSelectionMathTest.kt` | 20 tests de la selección y su estado (la conversión X↔tiempo se prueba en `AudioClipDetailTimeAxisMathTest`). |

## 4. Cambios de comportamiento a tener presentes

1. **Un dedo ya no desplaza la vista** con zoom: ahora selecciona. Se desplaza con dos dedos (ver §1).
2. Un toque simple sobre la onda deselecciona.

## 5. Límites conocidos (no incluidos a propósito)

- Sin desplazamiento automático al arrastrar la selección hasta el borde con zoom (se puede ampliar el rango con el ajuste de bordes, o alejando el zoom).
- Sin lectura de inicio/fin/duración (la captura de WavePad la muestra abajo); sin ajuste a cruces por cero.
- Sin «seleccionar todo / hasta el inicio / hasta el final».

## 6. Auditoría de la fase (revisión de ingeniería)

| Hallazgo | Resolución |
|---|---|
| **Duplicado:** la conversión X↔tiempo (`(scroll + x) / pxPerMs`, `ms · pxPerMs − scroll`) estaba repetida en el arrastre del cabezal (`detailScrubTarget`), el dibujo del cabezal, el tramo audible de la regla y, nueva, en la selección. | Una sola pareja de funciones en `AudioClipDetailTimeAxisMath.kt`, reutilizada por las cuatro. Equivalencia comprobada: el tramo audible siempre cae dentro de `0…duración`, así que acotar primero al archivo y luego al tramo da el mismo resultado que antes (cubierto por los tests existentes de `detailScrubTarget`). |
| **Fallo:** al pulsar REV (botón de esta misma ventana) la onda se invertía y la selección seguía en la misma posición, señalando otro audio. | La selección se descarta al cambiar `clip.reversed` (clave de `remember`). |
| Comprobado sin hallazgo: toque simple vs. arrastre (los movimientos consumidos cancelan el toque, sin falsos toques), doble toque + restitución, un dedo que pasa a pellizco, regla/cabezal que consumen sus toques antes que la selección, repintado solo de la capa al arrastrar (sin recomposición de la ventana), coste por evento (una asignación pequeña), selección fuera de vista con zoom (solo se dibuja lo visible). | — |
| Deliberadamente NO unificado: los bucles de marcas de la regla (`(t − scrollMs) · pxPerMs`) usan `scrollMs` precalculado por rendimiento y ya estaban así. | Sin cambio. |

## 7. Reproducir la selección

Con un rango seleccionado, al reproducir **solo suena ese tramo** (y, con el botón de bucle de la ventana activo, se repite).

- **Modelo:** `AudioClipPreview.selection` (`PreviewSourceSelection`, ms del ARCHIVO). `resolvedIn` convierte la selección al tramo del
  proyecto con `previewSelectionRangeOf` (`proyecto = inicio del clip + (archivo − recorte)`, la misma cuenta del cabezal) y
  lo usa en lugar del clip entero. El reloj de reproducción (`startPlaybackLoop`) y el mezclador (`syncAudioPreview`) **no cambiaron**: ya
  confinaban el cursor y dejaban sonando solo el carril al tramo del bucle; ahora ese tramo es más estrecho.
- **Se calcula en vivo:** si el clip se recorta o se mueve con la ventana abierta, el tramo sigue a la selección.
- **Solo lo audible:** una selección que sobresale del clip se queda con la parte que suena; si no toca nada de lo audible, el bucle usa el clip
  entero (nunca queda la vista previa en silencio).
- **Dónde empieza a sonar:** con el cursor fuera de la selección, salta al inicio de la selección; con el cursor dentro, suena desde ahí hasta
  el final del tramo y vuelve al inicio de la selección.
- **Bucle apagado:** la reproducción es la normal del proyecto y la selección no interviene (así estaba definido el botón: «desactivado =
  reproducción normal»).
- **Cuándo se avisa al editor:** solo al TERMINAR el gesto (soltar, toque que deselecciona, doble toque que restituye) y al descartarse la
  selección (otro clip, REV, cerrar la ventana). Avisar en cada movimiento escribiría el estado de toda la pantalla decenas de veces por segundo.
- Conexión: `AudioClipDetailPanel.onSelectionChange` → `AudioClipPreviewControls.onSelectionChange` → `EditorViewModel.setAudioClipPreviewSelection`.
- Tests: `AudioClipPreviewTest` (traducción al proyecto: recorte, bordes, fuera de rango, validación).
