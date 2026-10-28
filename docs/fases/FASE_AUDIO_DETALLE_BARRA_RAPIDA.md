# Ventana unificada del audio: barra de acceso rápido (Fase 1)

> **SUPERADO (2026-10-14).** La barra de acceso rápido de esta fase se eliminó: Cargar audio y REV pasaron a la cabecera de la
> ventana (`AudioClipDetailHeader`), NIVEL/PAN/VELOCIDAD/TONO se quitaron de esta ventana y el pie lo ocupa ahora la barra de
> vista general (`AudioClipDetailOverview`). Ver `docs/AUDITORIA_VENTANA_AUDIO_2026-10-13.md`. Este documento queda como historial.

## Decisión de diseño
Una SOLA ventana al hacer doble toque en un clip: la onda arriba y, fija al pie (justo encima de las pestañas
Módulos / Control / Keyframes), la barra de acceso rápido del clip. Un clip, un estado, un gesto de apertura.
Separarla habría duplicado el estado del clip y obligado a sincronizar dos superficies.

## Contenido de la barra (Fase 1)
Cargar/cambiar audio · Nivel (dB) · Pan · Reversa.

## Piezas
- `ui/AudioClipQuickBar.kt`: barra + deslizador propio (marca de valor de fábrica, imán 3 %, doble toque = reset,
  relleno desde el centro en Pan, color de identidad de la fila).
- `ui/AudioClipDetailPanel.kt`: la onda y la barra en una `Column`; el zoom/desplazamiento se reinician al cambiar de archivo.
- `AudioClipEditActions`: + `onVolumeChange`, `onPanChange`, `onReplaceSource` (mismas acciones del ViewModel que los módulos flotantes).
- `EditorViewModel.replaceAudioClipSource`: reemplaza el archivo conservando id, posición y mezcla; reinicia recorte/reversa/normalización;
  acota la duración para no pisar al clip siguiente; con undo.
- `MainActivity`: selector de audio para el reemplazo (`onReplaceAudioSourceClick`).
- Íconos vectoriales propios `ic_qa_*` (REV usa el ícono de dos flechas opuestas (trazo); el botón de cargar/cambiar audio reutiliza `ic_add_audio_clip`, el mismo de la capa de audio: misma acción, mismo ícono); etiquetas puras `quickLevelLabel` / `quickPanLabel` con pruebas.

## Auditoría y correcciones posteriores
- **Archivos locales de audio** (`ProjectStorage.localAudioFileName`): `audio_<clip>_<huella>.<ext>`. Con solo el id del clip, reemplazar el audio
  pisaba el archivo anterior y las cachés por URI + duración (forma de onda y render invertido en disco) servían datos viejos si la duración coincidía.
- **Fader de Nivel con curva en dB** (`quickLevelFromFraction/ToFraction`): 0 dB ≈ 76 % del carril, −6 dB ≈ 61 %, −20 dB ≈ 39 %.
- **Deslizador**: al agarrar el pulgar el valor ya no salta (conserva el desfase); llaves de `pointerInput` estables; semántica de accesibilidad
  (`progressBarRangeInfo`/`setProgress`); botones con `Role.Button` / `Role.Switch` (`toggleable`).
- **Etiquetas** con `Locale.US` (el punto decimal no depende del idioma del teléfono; las pruebas son deterministas).
- **`replaceAudioClipSource`**: no deja un punto de undo huérfano si el clip desapareció durante el sondeo del archivo.
- **Ventana**: la exclusión de gestos del sistema se centra en la zona de la onda (no en toda la ventana).
- Limpieza: `AUDIO_SIGNAL_WHITE_MIX` privada, `import lerp` explícito, `MultiTouchBackGuard.reset()` interna (solo pruebas).

## Cobertura de la barra Módulos / Control / Keyframes
La ventana (onda + barra rápida) ocupa hasta el borde inferior y **tapa** la barra Módulos/Control/Keyframes; al cerrarla la barra reaparece.
`AudioClipDetailHost` informa su estado (`onOpenChanged`, `DisposableEffect`: también avisa «cerrada» si el timeline sale de composición) →
`TimelineView.onAudioDetailOpenChange` → `EditorScreen.audioDetailOpen`, que condiciona `EditorBottomBar`. Se oculta (no se superpone) para que
sus botones no reciban toques por debajo de la ventana. La regla con el cursor y el Master siguen visibles.

## Cabecera de la ventana y cobertura del Master
- La ventana nace justo **debajo de la regla de tiempo** (la regla con el playhead sigue visible y usable) y tapa la fila Master.
  En su lugar hay una **cabecera propia** (`DetailHeader`, alto = Master + su divisor = `DETAIL_HEADER_HEIGHT`) con el botón de cerrar
  arriba a la derecha; el resto de la cabecera queda libre para las opciones futuras.
- `AudioClipDetailHost` se movió de la lista al contenedor del timeline (`fillMaxSize().padding(top = RULER_HEIGHT)`): un hijo
  desplazado fuera de los límites de su padre no recibe toques, así que cubrir el Master exigía subir el nivel, no desplazarlo.
- `labelColumnColorOverrides` (color Multicolor por fila) subió a nivel de función para que lo compartan la lista y la ventana.
- Con la ventana abierta, la línea del playhead del timeline mide solo la regla (la ventana tiene su propio cursor).
- La X salió de la zona de la onda: el texto de datos ya no reserva espacio a la derecha.

## Actualización: la ventana también tapa la regla del playhead
Decisión final de diseño: la ventana cubre **todo** el timeline (regla con el playhead + Master + lista + pestañas inferiores). Su cabecera
(`DETAIL_HEADER_HEIGHT` = regla + Master + divisor) reemplaza a la regla y al Master, con la X de cerrar arriba a la derecha.
`AudioClipDetailHost` es ahora el ÚLTIMO hijo del contenedor del timeline, así que se dibuja y recibe los toques por encima del playhead
arrastrable y del ícono de capas (que quedan debajo, sin interferir). Al cerrar, regla, Master y pestañas reaparecen intactos.

## Corrección: cabecera delgada y apilado
- **Ícono de capas visible a través de la ventana**: ese ícono usa `zIndex(12f)`, y el orden de apilado de Compose manda sobre el orden de
  declaración. La ventana ahora usa `AUDIO_DETAIL_Z_INDEX = 100f`, así que queda por encima de TODO el timeline (ícono, playhead, lista) y nada
  se ve ni recibe toques a través de ella.
- **Cabecera**: de 57 dp (regla + Master + divisor) a **40 dp** propios (`DETAIL_HEADER_HEIGHT` en `AudioClipDetailPanel`), con el botón de cerrar
  (36 dp táctiles, disco de 22 dp) centrado verticalmente arriba a la derecha y un filete inferior. La ventana cubre igual todo el timeline; la
  onda gana el espacio que la cabecera ya no usa.

## Corrección: la ventana bloquea los toques (no «atraviesa»)
En Compose un elemento sin manejo de toques NO los detiene: el hit-test sigue hacia los hermanos de debajo. Por eso, tocar la cabecera vacía
(o un hueco de la barra rápida) activaba el timeline de atrás (p. ej. el menú «Color» del ícono de capas). Solución: `pointerInput` en la raíz de
`AudioClipDetailPanel` que captura todo evento dentro de sus límites. Los hijos (onda, deslizadores, botones, X) siguen recibiendo sus gestos
primero. Verificar en dispositivo: tocar la cabecera vacía, los huecos de la barra y la zona del ícono de capas → no debe pasar nada detrás.

## Auditoría final de la ventana (correcciones)
1. **Panel inferior abierto tapaba la ventana**: los paneles Módulos/Control/Keyframes son hermanos de `TimelineView` en `EditorScreen` con `zIndex(5f)`; el
   `zIndex(100f)` de la ventana solo ordena dentro del timeline. Con un panel abierto, la ventana quedaba debajo. Ahora abrir la ventana cierra el panel.
2. **Lectura engañosa**: el texto de datos mostraba el zoom como «1.8×», idéntico al formato de la Velocidad. Ahora dice «Zoom 1.8×».
3. **Localización**: el zoom se formateaba con la configuración regional del teléfono (coma decimal en español); ahora `Locale.US`, como el resto de etiquetas.
4. **Código flotante**: se revirtió el ajuste de `contentHeight` del playhead (la ventana ya cubre y bloquea el timeline; no hacía falta).

## Corrección: la onda invadía la cabecera
Con zoom de amplitud alto (`Amp 2.7×`) el dibujo de la onda sobrepasaba el alto de su zona y se pintaba sobre la cabecera (Compose no recorta a los hijos
por defecto, y el hijo posterior se dibuja encima del hermano anterior). La zona de la onda ahora usa `clipToBounds()`: nada se dibuja fuera de ella.
