# Fase — Panorama y volumen en la barra flotante de la capa de audio (2026-10-14)

Referencia de producto: FL Studio Mobile (deslizadores finos de panorama y volumen en la fila del carril).

## Cambio
| Antes | Ahora |
|-------|-------|
| Módulos → Audio → **Volumen** y **Pan** abrían ventanas flotantes con un deslizador. | Los dos módulos **se eliminaron** del cajón (`AudioModuleId` queda en `MUTE`, `TRIM`, `LOOP`, `FADE`). |
| — | Cada capa de audio muestra **Pan** (corto, relleno desde el centro, con oídos L/R) y **Vol** (largo, rayita en el 100 %) en su barra flotante, a la derecha de las acciones. |

No existe un segundo control: el «Nivel» de la ventana ampliada ya se había retirado y el volumen se edita en un único lugar.

## Diseño
- `AudioTrackMixSliders.kt`: matemática pura (`audioMiniSliderFraction`, `audioMiniSliderValueAt`, `audioMiniSliderFill`, rótulos), el deslizador
  (`AudioMiniSlider`, responde desde el primer toque, con semántica de accesibilidad y `setProgress`) y el bloque `AudioTrackMixSliders`.
- Panorama con **oído a cada extremo**, ambos **mirando hacia adentro** (la concha abre hacia el centro de la barra): `ic_ear_left.xml`
  (izquierdo, con «L») e `ic_ear_right.xml` (derecho, con «R»). Dibujo propio y anatómico de trazo (hélix con lóbulo, antihélix y trago,
  viewBox 24 × 24, trazo 1,8). Cada lado es un recurso propio —el derecho es el espejo exacto del izquierdo, generado al diseñarlo—, así la
  orientación vive en el archivo y no se voltea en tiempo de ejecución. `earDrawableRes(right)` elige el recurso y
  `StereoEarIconsStructureTest` blinda que existan, que se espejen entre sí y que no vuelva el `scaleX = -1f`.
  *Corrección (2026-10-15):* antes un solo recurso (Tabler «ear», abertura a la izquierda) se espejaba para el derecho, y quedaban abriendo
  hacia afuera.
- **Imán al valor por defecto** (2026-10-15): al acercar el dedo a la rayita —centro del panorama, 100 % del volumen— el tirador se pega a ella
  y vale exactamente el valor por defecto; para soltarlo hay que alejarse del radio. Alcance: 5 dp a cada lado, acotado al 10 % del recorrido
  para que en el panorama corto no se coma medio recorrido (`audioMiniSliderSnapRadiusPx` / `audioMiniSliderInSnapZone` / `audioMiniSliderSnap`,
  puras y con test). Aplica a toque y arrastre; vibra suave (`TextHandleMove`) solo al ENTRAR en la zona. No cambia el doble toque (reseteo)
  ni el valor tecleado en la ventana del volumen. Ajuste fino: `MINI_SLIDER_SNAP_RADIUS` y `MINI_SLIDER_SNAP_MAX_TRAVEL_FRACTION`.
- **Ícono de volumen** al inicio del deslizador (`ic_volume` con volumen, `ic_volume_off` en cero; geometría fiel a los SVG entregados). Los
  dos ocupan el mismo lugar y se alternan con un fundido de 120 ms (`VolumeGlyph`); «en cero» = el volumen se lee como 0 %
  (`audioVolumeIsMuted`, mismo redondeo que el rótulo). Mira el volumen del clip, no el interruptor Silencio.
- Dos disposiciones del mismo bloque: **en fila** (panel lateral, sin rótulos) y **apilada** con íconos (acordeón del pie, columna angosta de capas,
  que se usa mientras hay una pestaña inferior abierta).
- `LayerActionAccordion` admite `extraContent`/`extraContentHeight` y `rememberRowActionsPanelPlacement` el alto extra (`extraAccordionHeight`), de
  modo que la decisión lateral/pie cuenta con el alto real del acordeón.
- Quién edita: el carril puede tener varios clips y volumen/panorama son de **clip**; los deslizadores actúan sobre el clip objetivo del carril
  (`AudioTrack.moduleTargetClip`: el seleccionado o, si no hay, el que está bajo el cabezal). Llaman a `setAudioVolume` / `setAudioPan` (mismos
  setters de siempre; un arrastre = un paso de deshacer; detente de ±2 % en el centro del panorama).

## Pruebas
`AudioTrackMixSlidersTest`: fracción, del toque al valor, tramo relleno (normal y centrado), rótulos, umbral del ícono sin sonido.

## Validar en el dispositivo
1. Seleccionar una capa de audio: aparecen Pan y Vol junto a lápiz/paleta/candado/papelera.
2. Con el cajón Módulos abierto: el acordeón del pie muestra ambos apilados (oídos L/R sobre el panorama) y editan igual.
3. Arrastrar Pan: relleno desde el centro; cerca del centro se pega al 0. Arrastrar Vol: la rayita marca el 100 %.
4. Con dos clips en el carril: editan el seleccionado.
5. Deshacer tras un arrastre: vuelve al valor anterior en UN paso.
