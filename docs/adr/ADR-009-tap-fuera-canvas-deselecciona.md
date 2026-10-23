# ADR-009 — Tocar la franja negra de "letterbox" también deselecciona (marco morado que no se soltaba)

**Estado:** Decidido e implementado.
**Referencia:** ADR-005 Fase F (el "slot" del preview y el letterboxing
negro), sesión de soporte del 13 de septiembre de 2026 (captura del
cliente sobre el proyecto IMAX, tocando la franja negra a la derecha del
canvas).

## Contexto

`EditorScreen.kt` ya tenía resuelto (antes de este ADR) el caso "tocar
un hueco vacío DENTRO del canvas deselecciona la capa" — comentario
`--- ARREGLADO: tocar un espacio vacío del canvas...` en el
`pointerInput` del Box interior (el rectángulo del Canvas real, con
`.aspectRatio(state.canvas.aspect.ratio)`).

El cliente reportó el mismo síntoma pero en un lugar distinto: al tocar
la franja negra de "letterbox" que pinta `ADR-005/Fase F` — el Box
EXTERIOR (el "slot" del preview, `.background(Color.Black)`) — cuando el
aspecto del Canvas del proyecto no llena por completo ese slot (un
IMAX horizontal dentro de un slot más alto que ancho, como en la
captura), el marco morado de selección no se soltaba.

Causa: toda la lógica de tap-para-deseleccionar (igual que el resto de
selección/arrastre/pellizco) vive en el `pointerInput(Unit)` del Box
INTERIOR únicamente. El Box exterior no tenía ningún manejador de
gestos propio. Un toque que cae físicamente en la franja negra —fuera
de los límites de layout del Box interior— nunca llega a ese
`pointerInput`: Compose solo entrega el evento a los nodos cuyos límites
contienen el punto tocado, y en esa zona el único nodo es el Box
exterior. Sin manejador ahí, el toque no hacía absolutamente nada —ni
seleccionaba, ni deseleccionaba, ni consumía el evento.

## Decisión

Agregar un `pointerInput(Unit) { detectTapGestures { viewModel.clearSelection() } }`
al Box exterior (el slot/letterbox), con el mismo criterio ya usado
adentro: un toque simple (no arrastre — `detectTapGestures` ya distingue
eso por su cuenta, sin necesidad del umbral manual `tapSlopPx` que usa
el Box interior para su gesto más complejo de selección/arrastre/
pellizco) limpia la selección.

No hace falta ningún guard adicional (como `editIdForHitTest == null` en
el Box interior, para no salir del modo "Editando imagen" ver ADR
anterior sobre ese modo): un toque en la franja negra está, por
definición, fuera del Canvas — nunca puede ser parte de una edición
aislada de una capa que vive dentro del lienzo.

## Por qué no interfiere con el Box interior

El Box interior está centrado DENTRO del exterior (`contentAlignment =
Alignment.Center`) y es más chico (por el propio letterboxing). Un toque
que cae dentro de sus límites lo recibe primero el Box interior (es el
nodo más profundo cuyos límites contienen el punto) — su propio
`pointerInput`, con toda la lógica de selección/arrastre existente, sigue
resolviendo esos toques exactamente igual que antes. El manejador nuevo
del Box exterior solo entra en juego para toques que caen FUERA de esos
límites — la franja negra en sí.

## Alcance del fix

Un solo punto de cambio: el modifier del Box exterior en
`EditorScreen.kt` (la sección "ADR-005, Fase F: el slot del preview").
No se tocó el Box interior, `viewModel.clearSelection()`, ni ningún otro
gesto existente.

## Verificación pendiente en dispositivo real

Mismo estado que ADR-007/ADR-008: cambio hecho sobre el código fuente
sin Android SDK/Gradle disponible en esta sesión. Antes de cerrar:

1. Compilar y confirmar que no rompe ningún gesto existente del Box
   interior (selección, arrastre, pellizco, doble-tap para reemplazar
   imagen, modo "Editando imagen", modo reordenar manijas).
2. Abrir un proyecto con formato que deje franja negra visible (como el
   IMAX de la captura), seleccionar el fondo, tocar la franja negra, y
   confirmar que el marco morado desaparece.
3. Confirmar que tocar la franja negra SIN ninguna capa seleccionada no
   causa ningún efecto visible ni error (debería ser un no-op silencioso,
   ya que `clearSelection()` sobre una selección ya vacía no debería
   tener efecto).
