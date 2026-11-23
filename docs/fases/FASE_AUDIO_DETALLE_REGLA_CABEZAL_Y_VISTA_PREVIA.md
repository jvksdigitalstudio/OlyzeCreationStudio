# Ventana del audio: regla de tiempo, cabezal arrastrable y vista previa en bucle

## Decisión de diseño
Tres piezas que se refuerzan entre sí, todas sobre el MISMO cursor del proyecto (la ventana no tiene transporte propio: el Play y el
cursor son del proyecto; un segundo cabezal habría obligado a sincronizar dos relojes):

1. **Regla + cabezal** en la ventana ampliada. En reposo solo existe el cabezal, entre los números; la línea hacia abajo NO existe hasta
   que se mantiene pulsado el cabezal.
2. **Vista previa en bucle** del clip abierto: al abrir la ventana el cursor pasa al inicio de la señal del clip y el bucle queda activado;
   al dar Play solo suena ese clip y, al acabar, vuelve a empezar. Nunca arranca sola.
3. **Margen lateral** del eje de tiempo, para que el cabezal en 0:00 (ahora el estado normal al abrir) se vea entero.

## Comportamiento
- **Cabezal**: pulsarlo despliega la línea guía hacia abajo y una lectura del tiempo exacto; arrastrarlo mueve el cursor al instante; al soltar
  la línea se repliega. Con zoom, llevarlo a menos de 56 dp de un borde desplaza la vista sola (velocidad ∝ cuadrado de lo adentrado).
  Se detiene en los extremos del clip (como mucho el último ms: el clip es `[inicio, fin)`). Con el cursor FUERA del clip no desaparece: queda atenuado en el borde más cercano del tramo audible.
- **Arrastre con el proyecto sonando**: pausa al agarrar y retoma al soltar (`beginScrub`/`endScrub`, el mismo par que usa el playhead del
  timeline), para que el reloj no pise el cursor que mueve el dedo.
- **Abrir un clip** (doble toque): el cursor va a `timelineStartMs` del clip, el bucle se activa, y si el proyecto sonaba se detiene.
- **Bucle activado**: el reloj confina el cursor al tramo del clip y, al llegar al final, vuelve al inicio conservando el sobrepaso del cuadro;
  solo suena el carril del clip (los demás se sincronizan en pausa, no se liberan). **Desactivado**: reproducción normal.
- **Cerrar** (X, «atrás» o el clip desaparece): termina la sesión; si el bucle sonaba, se detiene la reproducción.
- Botón del bucle en la cabecera, a la izquierda de la X: ícono blanco apagado / color de la capa activado; contenedor neutro en ambos estados
  (como el resto de botones de la ventana); `Role.Switch`.

## Piezas
- `engine/audio/AudioClipPreview.kt`: `AudioClipPreview(clipId, loopEnabled)` (estado de sesión), `resolvedIn`; el confinamiento del cursor vive hoy en `PreviewLoopPlan.confine` (ver `FASE_AUDIO_DETALLE_SELECCION_MULTIPLE.md`).
  Solo guarda QUÉ clip se audita: el tramo y el carril se resuelven EN VIVO contra los carriles, así que si el clip se recorta, mueve o cambia de
  velocidad con la ventana abierta, el bucle sigue su tramo real (no uno copiado y desfasado).
- `AudioPreviewPlayers.sync(..., soloTrackId)`: aislamiento del carril.
- `EditorViewModel`: `audioClipPreview` en `EditorUiState` (no se guarda ni entra al undo); `enterAudioClipPreview` / `setAudioClipPreviewLoop` /
  `exitAudioClipPreview`; el bucle vive en `startPlaybackLoop` (el ÚNICO sitio que avanza el cursor); `syncAudioPreview(forceSeek)` reposiciona
  el audio en la misma vuelta del bucle.
- `ui/AudioClipDetailRuler.kt` (regla estática: marcas mayores/medias/menores, números, barra del tramo que suena, cuadrícula, tramo atenuado) y
  `ui/AudioClipDetailPlayhead.kt` (cabezal, guía, lectura, gesto, autodesplazamiento) en capas de dibujo independientes: arrastrar repinta solo el cabezal.
- `ui/AudioClipDetailTimeAxisMath.kt`: matemática pura (dedo→tiempo acotado al clip, zona de agarre, autodesplazamiento, subdivisiones, cabezal en reposo).
- `ui/AudioClipPreviewControls.kt`: un solo parámetro hacia `TimelineView` (clase pública, igual que `AudioClipEditActions`).
- `res/drawable/ic_loop_preview.xml`: vector fiel al SVG entregado, sin color propio (lo tiñe la UI).

## Auditoría y correcciones
- **Parpadeo al abrir**: entrar a la sesión en un efecto dejaba un cuadro con el botón apagado y el cabezal en su sitio viejo. Ahora `onEnter` se llama
  en el doble toque (`TimelineView`), antes de que la ventana se componga; el efecto de la ventana solo se ocupa de SALIR.
- **Cabezal cortado en 0:00** (visto en pruebas): el margen lateral (`DETAIL_TIME_GUTTER_DP` = 16 dp) lo deja entero; las etiquetas se desplazaron a
  11 dp de su marca para que el cabezal en 0:00 no las tape. Las X de los gestos se miden desde el lienzo y se les resta el margen.
- **Cabezal invisible fuera del clip** (visto en pruebas): ya no desaparece; queda atenuado en el borde y se puede agarrar.
- **Carrera del reloj de reproducción** (previa, la dejaba al descubierto el nuevo arrastre): el reloj calculaba el siguiente cuadro desde el estado leído
  ANTES de la espera, así que un `seekTo` llegado durante ella (tocar la onda con el proyecto sonando) se pisaba. Ahora lee el estado fresco tras el `delay`.
- **Tipo expuesto**: `TimelineView` es público; `AudioClipPreviewControls` pasó de `internal` a público (error de compilación seguro si no).
- **Gestos de borde**: con el cursor al inicio, el cabezal queda pegado al borde izquierdo y arrastrarlo a la derecha se confundiría con «atrás»; la regla
  reclama sus bordes. Límite de Android: 200 dp por borde EN TOTAL → la franja central bajó de 200 a 160 dp (160 + 36 de la regla).
- **Etiquetas**: `drawText` junto al borde derecho lanzaba `IllegalArgumentException` (ancho negativo); se pasa `size` explícito y se omite si no cabe.
- **Subdivisiones de la regla**: la escalera de pasos incluye 2, 5 y 10 min, que una regla por mantisa subdividía en valores no redondos (24 s, 100 s); tabla explícita.
- **Dos «loop» distintos, a propósito**: Loop del clip (ítem del menú del clip; repite el audio hasta llenar el proyecto; se guarda y cambia lo exportado — el ícono `ic_loop` se retiró el 2026-10-18, ver `FASE_AUDIO_LOOP_EN_MENU_DEL_CLIP.md`) y `ic_loop_preview` (este
  bucle de vista previa: solo de sesión, no cambia nada). Íconos y estado separados para que no se confundan.
- **Segunda pasada de auditoría** (sobre el código ya integrado):
  - *Aislamiento de audio*: los carriles que no son el aislado se silenciaban llamando a `pause()` desde fuera, sin limpiar el estado interno del reproductor
    (`playingClipId`, `lastProjectTimeMs`). Ahora se sincronizan como si el proyecto estuviera en pausa (`isPlaying = false`), la vía canónica del propio reproductor:
    más correcto y una sola llamada, sin ramas.
  - *El bucle ampliaba el proyecto*: el reloj llama a `growIfApproachingEnd`, que alarga la duración al acercarse al final. En el bucle el cursor está confinado a
    contenido que ya existe, así que no se llama: escuchar el último clip ya no alarga el proyecto (esa duración se guarda).
  - *Arrastrar hasta el extremo*: el cursor caía en `fin` exacto del clip (fuera del intervalo semiabierto `[inicio, fin)`; cabezal atenuado, y `seekTo` ampliaba el proyecto
    si el clip terminaba donde el proyecto). El último instante alcanzable es `fin − 1 ms`.
  - *Cerrar en mitad de un arrastre*: `beginScrub` ya había pausado el proyecto, `exit` no encontraba nada que pausar y el `finally` del gesto llamaba a `endScrub`, que
    retomaba la reproducción sin bucle por todo el proyecto. `exit` anula esa reanudación pendiente.
  - *Doble lectura de estado en el reloj*: tras la corrección de la carrera quedaba `_uiState.value` leído dos veces y un comentario desactualizado; ahora una sola lectura (`state`).
  - *Ripple del botón de bucle*: cuadrado de 40 dp alrededor de un botón redondeado; recortado como el botón de cerrar.
- **Tercera pasada (regla, sobre el código real y con las cifras del tablet)**: se replicó numéricamente la regla (paso, ancho de etiquetas con
  métricas de Roboto, densidades 1,0–3,5, escala de fuente 0,85–2,0, duraciones 0,8 s–10 h, zoom 1×–8192×).
  - *Verificado correcto*: tablet de 1 624 px de ancho y archivo de 3,420 s en 1× → paso de **200 ms**, 91,5 px entre marcas, etiqueta de ~76 px
    (aire ≈ 15 px ≥ 6 dp exigidos); coincide con la captura. Marcas, cuadrícula, barra del tramo audible y cabezal comparten escala y margen.
  - *Aire entre números*: el mínimo era 6 dp (los números cada 200 ms quedaban casi pegados, ~15 px). Se subió a 24 dp (`LABEL_GAP_DP`), el rango de
    los editores profesionales: en el tablet, 1× y 3,420 s, el paso pasa a 500 ms con ~150 px de aire.
  - *Defecto corregido*: la escalera de pasos terminaba en 10 min. Con audios de ≥ 2–3 h (el proyecto admite hasta 3 h y el archivo del clip puede
    ser más largo) ninguna marca cumplía la separación, caía en el último paso y los números se pisaban. Se amplió con 15 min, 30 min y 1 h
    (`DETAIL_GRID_STEPS_MS`) y sus subdivisiones redondas (5 / 5 / 10 min) en `detailRulerSubdivisions`. Tests: `AudioClipDetailMathTest`
    (3 h en 1×, 12 h en 1×, `180:00`) y `AudioClipDetailTimeAxisMathTest` (subdivisiones nuevas).
  - *Defecto corregido (visto en el tablet)*: una etiqueta cuya marca queda cerca del borde derecho se dibujaba recortada por la pantalla; en 1× con
    3,420 s la marca de 3,400 s dejaba un \"0:\" suelto (~18 px de 76) pegado al borde, de forma permanente. Ahora, por la derecha, un número solo se
    dibuja si cabe entero (`detailRulerLabelVisible`); por la izquierda sigue deslizándose fuera con su marca. Tests en `AudioClipDetailTimeAxisMathTest`.
- Limpieza: eje antiguo (`AudioClipDetailTimeAxis`) sustituido por regla + cabezal sin dejar rastro; constantes de cuadrícula movidas; imports sin uso
  eliminados; `QuickNeutralFill/Border` compartidos (una sola fuente del color neutro) en lugar de duplicarlos.

## Pruebas
- `AudioClipPreviewTest`: región, `confine` (antes, dentro, final exacto, sobrepaso, tick más largo que el clip), `previewLoopRangeOf`, `resolvedIn`.
- `AudioClipDetailTimeAxisMathTest`: arrastre acotado al clip (escala, desplazamiento, loop), zona de agarre, autodesplazamiento, subdivisiones, cabezal en reposo.

## Pendiente de verificar en dispositivo
Esta fase se escribió y auditó sin poder compilar ni ejecutar la app (sin Gradle ni dispositivo en el entorno de desarrollo): lo verificado fue el balance
sintáctico, la coherencia de firmas contra el código real, y la lógica pura contra una réplica numérica. Falta confirmar en el tablet:
1. Abrir un clip con el cursor antes / dentro / después de él: el cursor queda al inicio del clip, el bucle activo, sin sonar.
2. Play: suena solo ese clip y repite sin acumular desfase. El corte audible en la vuelta es el de un `seekTo` de `MediaPlayer` (no hay empalme sin hueco con este motor).
3. Arrastrar el cabezal sonando y parado, con zoom 1× y alto; autodesplazamiento en los bordes.
4. Desactivar el bucle sonando: los demás carriles se incorporan en ≤ 50 ms.
5. Cerrar con X y con «atrás»: el cursor queda en el inicio del clip y la reproducción detenida.


## Regla de nivel profesional (versión final)

Pedida expresamente: «la máxima regla profesional». Criterio de diseño (el de las reglas de los editores de audio: pasos redondos
que siguen al zoom, números que nunca se pisan, jerarquía de marcas, cuadrícula alineada con las marcas, el rango seleccionado
visible contra el tiempo). Lo que ya cumplía se mantiene; lo que se añadió o cambió:

| Mejora | Detalle |
|---|---|
| **Decimales justos** | `detailLabelDecimals(stepMs)`: los decimales mínimos que escriben el paso con exactitud (0 en segundos, 1 en 100/200/500 ms, 2 en 10/20/50 ms, 3 en 1/2/5 ms…). `0:00.5` en vez de `0:00.500`: número más corto, se lee antes y deja más aire. |
| **Horas** | Desde 1 h la etiqueta es `h:mm:ss` (`3:00:00`), no `180:00`. Afecta también a la lectura del cabezal. |
| **Separación de lectura** | Pitch mínimo entre números de 64 dp (antes 48) y 24 dp de aire entre texto y texto. En el tablet (3,420 s en 1×): paso de 500 ms, etiquetas `0:00.0 · 0:00.5 · 0:01.0…`. En un teléfono de 412 dp: paso de 1 s, `0:00 · 0:01 · 0:02`. |
| **Número cortado en el borde** | Por la derecha un número solo se dibuja si cabe entero (`detailRulerLabelVisible`); por la izquierda se desliza fuera con su marca. |
| **Escalera hasta 1 h** | Pasos de 15 min, 30 min y 1 h (`DETAIL_GRID_STEPS_MS`) con sus subdivisiones: los audios de 2–3 h ya no pisan los números. |
| **Selección sobre la regla** | Banda tenue sobre la regla y bordes que arrancan en seco en su filo inferior (ver `FASE_AUDIO_DETALLE_SELECCION_DE_RANGO.md`). |

Cobertura: `AudioClipDetailMathTest` (formato, decimales por paso, horas, escalera), `AudioClipDetailTimeAxisMathTest` (subdivisiones, borde derecho).

## Toque sobre el cabezal = reproducir

- **Toque simple sobre el icono del cabezal** (rápido y sin mover el dedo): inicia la reproducción desde donde está el cursor.
  Decisión en `detailIsHeadTap`: empieza sobre el cabezal, termina levantando el dedo, se mueve ≤ umbral de toque del sistema y dura menos
  que la pulsación larga del sistema.
- **Mantener presionado o arrastrar**: sigue siendo el recorrido de la línea de tiempo (pausa mientras se agarra y retoma al soltar si
  sonaba). No reproduce al soltar.
- **Nunca pausa desde el cabezal:** pausar es del botón principal de la barra superior. Si ya suena, el toque no cambia nada
  (`EditorViewModel.startPlayback()` es idempotente; a diferencia de `togglePlayback()`).
- Tocar la regla fuera del cabezal solo lleva el cabezal ahí, como antes.
- Conexión: `EditorScreen.onPlay` → `TimelineView.onPlay` → `AudioClipDetailHost.onPlay` → `AudioClipDetailPanel.onPlay` → cabezal.
