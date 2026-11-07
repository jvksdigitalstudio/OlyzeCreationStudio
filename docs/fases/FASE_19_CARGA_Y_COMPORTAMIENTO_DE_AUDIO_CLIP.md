# FASE 19 — Carga y comportamiento profesional del clip de audio

> Alcance: `+` → `Audio` (carga) y reproducción en vivo del carril.
> Referencia de diseño: convención estándar de playlist de DAW (FL Studio
> Mobile/Desktop), tal como ya la documenta `AudioClip.kt`. **No se pudo
> consultar la documentación oficial en línea desde este entorno**: la
> colocación "en el cabezal, sin pisar" es decisión de diseño a validar
> contra la app real.
>
> Sin compilador/Gradle en esta sesión: verificado por revisión manual y
> balance de llaves; falta `./gradlew testDebugUnitTest assembleDebug` y
> prueba en dispositivo.

## Bug 1 (pérdida de datos) — cargar un audio borraba los demás clips
`EditorViewModel.importAudio` asumía "un único clip": hacía
`copy(clips = listOf(newClip))` y reutilizaba el `id` del primero. Con 3
clips en el carril (captura del usuario), elegir `+ → Audio` reemplazaba el
primero y **eliminaba los otros dos** en silencio.

**Corrección:** cargar AGREGA un clip (`AudioTrack.withClip`), con id
nuevo, largo = duración de su archivo, ajustes por defecto, ubicado en el
cabezal; si ahí hay otro clip, justo a continuación del que estorba
(`firstFreeStartMs`, función pura). Se conserva la identidad del carril.
El estado se lee después de las esperas de IO (el cabezal/carril pudo
cambiar durante el sondeo del archivo).

## Bug 2 — el preview en vivo solo evaluaba el clip al pulsar Play
`activeClipAt` respetaba el largo del clip pero nadie lo reconsultaba
mientras corría la reproducción: un clip posterior nunca arrancaba solo,
uno terminado seguía sonando, el loop no reiniciaba (el `MediaPlayer` se
detenía al final del archivo) y cada edición con Play activo hacía seek.

**Corrección:** `AudioPreviewPlayer.sync(track, projectTimeMs, isPlaying)`
decide por cuenta propia (entrar/salir de clip, cambio de clip, fin de
archivo con loop, corrección de deriva > 300 ms) y solo hace seek cuando
hace falta. El tick de `startPlaybackLoop` lo invoca a ≈20 Hz. La posición
dentro del archivo vive en `sourcePositionAtProjectMs` (misma regla que el
export: 1.ª pasada desde `trimStartMs`, vueltas siguientes desde 0).
`AudioApi.previewPlayFrom/previewSeekTo` ahora usan el clip activo, no
"el primero".

## Cierra el gap de ADR-012
"Arranque automático a mitad de reproducción en vivo" queda resuelto.

## Tests
`AudioClipPlacementAndPositionTest` (JUnit puro, 16 casos).

## Límites conocidos (no tocados)
- Preview = un solo `MediaPlayer`: con clips solapados suena el último
  agregado; el export sí mezcla todos.
- `ensureLoaded` usa `prepare()` síncrono al cambiar de archivo (hilo
  principal); migrar a `prepareAsync` es una fase aparte.
- El export de audio no aplica `speedKeyframes`/`freezeFrames`; el preview
  tampoco. Si se activa "Tiempo", ambos deben alinearse.

---

# Addendum — Paridad con FL Studio Mobile (manual oficial: Playlist y Editors)

Fuente: manual en línea de FL Studio Mobile (Image-Line), secciones
Playlist y Editors. Cada opción se implementó de punta a punta (modelo →
persistencia → undo → export → preview → UI → tests).

## 1. Cortar (Clip Controls > More > Cut)
`EditorViewModel.cutAudioClip`: portapapeles + quitar del carril en UN
cambio de estado (un solo autosave). Menú del clip: "Cortar".

## 2. Combinar (Combine) — "Unir con el siguiente"
`AudioClip.canMergeWith/mergedWith`, `AudioTrack.mergeCandidateAfter`,
`isExactContinuation` (pura). Une SOLO cuando el resultado suena idéntico:
mismo archivo, B empieza donde termina A en el timeline y en el archivo
(incluido el cruce de la vuelta del loop), mismo volumen/mute/loop.
Conserva el fade-in del primero y el fade-out del segundo. Es el inverso
exacto de "Dividir en el cursor". En cualquier otro caso es no-op.

## 3. Pan por clip
Campo `AudioClip.pan` (-1..1), persistido (`AudioClipData.pan`, default 0
para proyectos viejos) y en el snapshot de undo. Ley de BALANCE
(`panGains`): el centro no toca la señal; al desplazarse solo baja el canal
opuesto. Única fuente de verdad para el export (`applyBalancePan`; un clip
mono se abre a estéreo con `prepareForPan` solo si pan != 0) y el preview
(`MediaPlayer.setVolume(L, R)`). Detente de ±2 % al centro. Módulo "Pan" en
el cajón de Módulos (ventana flotante con slider).

## 4. Normalizar (no destructivo)
`AudioClip.normalizeGain` (1 = sin normalizar). Se calcula con el pico REAL
del tramo audible (`AudioWaveform.sourcePeak` / `absolutePeakInRange`,
`audibleSourceRangeMs`) hacia -1 dBFS, con tope +24 dB
(`normalizeGainForPeak`). Se multiplica por el volumen en el export
(`applyVolumeAndFades(gainMultiplier)`) y en el preview; como `MediaPlayer`
no puede superar 1.0, el exceso lo aplica un `LoudnessEnhancer` (permiso
`MODIFY_AUDIO_SETTINGS` añadido al manifest) — de paso el preview ahora sí
refleja volúmenes > 100 %. Menú del clip: "Normalizar" / "Quitar
normalización".

## Corrección transversal
Los módulos flotantes de audio (Volumen, Silencio, Recorte, Loop, Fade,
Pan) y "Renombrar" editaban SIEMPRE el primer clip. Ahora operan sobre el
clip bajo el cabezal (`AudioTrack.moduleTargetClip`; no excluye silenciados,
para poder reactivarlos) o el más cercano si el cabezal cae en un hueco.

## Tests nuevos
`AudioClipPlacementAndPositionTest` (+ `AudioClipMergeRuleTest`),
`AudioPanTest`, `AudioNormalizeTest`.

## 5. Reverse (invertir)
`AudioClip.reversed` (persistido y en undo). Semántica de "Reverse sample"
de FL: se invierte el ARCHIVO fuente y recorte/loop operan sobre esa versión.
Al alternar, `reversedTrimStartMs` recalcula el recorte para que suene el
MISMO tramo (alternar dos veces lo restituye).
- Export: `AudioProcessor.prepareDecoded` invierte el PCM por FRAMES
  (`reverseFrames`; en estéreo L,R viajan juntos) antes de recorte/loop/pan.
- Preview: `MediaPlayer` no reproduce al revés, así que
  `ReversedAudioCache` renderiza UNA vez por archivo un WAV 16-bit invertido
  en `cacheDir` (hilo único, escritura atómica, máx. 6 archivos). El player
  lo trata como la fuente del clip; mientras no esté listo el clip no suena
  (nunca suena al derecho por error) y `sync` reintenta cada tick.
- Forma de onda dibujada espejada (`AudioWaveform.reversedCopy`);
  Normalizar espeja el tramo medido; `canMergeWith` exige igual `reversed`.

## 6. Selección de tiempo (Insertar espacio / Duplicar / Borrar / Borrar espacio / Recortar)
Motor puro `AudioTimeRangeOps.kt`: `planTimeRangeOp` trabaja solo con
geometría (`ClipSpan` → `ClipPiece`) y todas las operaciones se reducen a
recortar cada clip contra los bordes de la selección y recolocar trozos.
`AudioClip.slicePiece` construye cada trozo con la regla única de posición
(`sliceTrimStartMs` = `sourcePositionAtProjectMs`), por lo que cortar a mitad
de la vuelta N de un loop continúa el audio exacto; el fade-in solo sobrevive
si el trozo arranca donde el clip y el fade-out si termina donde él.
Semántica: INSERTAR abre silencio desde el inicio y corre lo posterior;
DUPLICAR copia la selección a continuación de ella y corre lo posterior (la
selección pasa a la copia: repetir encadena); BORRAR deja el hueco; BORRAR
ESPACIO cierra el hueco; RECORTAR conserva solo lo de dentro sin moverlo.
VM: `selectAudioTimeRange/ClipRange/RangeAtPlayhead`, `applyAudioTimeRangeOp`
(undo FORZADO — estas ops sí reordenan clips; sin cambio real no ensucia el
historial; el timeline crece si hace falta). UI: overlay con 2 manijas con
imán (cursor, bordes de clips, extremos del proyecto y rejilla) y menú al
tocar el cuerpo; entradas "Seleccionar clip" y "Seleccionar rango en el
cursor". Mientras hay selección su zona captura los toques (se quita desde
su menú).

## 7. Rejilla musical con snap
`AudioTrack.gridBpm` + `gridStep` (OFF/Compás/1/4/1/8/1/16), persistidos con
el carril y en undo. Paso en `Double` (`gridStepMs`): a 128 BPM un pulso es
468.75 ms; se multiplica el paso exacto y recién entonces se redondea, sin
deriva acumulada. `snapToTargetsMs`/`snapMovedClipStartMs` ganan un
parámetro `gridMs` (default 0 = comportamiento anterior): la línea de rejilla
compite por distancia con los bordes de clip/cursor y ante empate gana el
borde. Líneas de rejilla dibujadas tras los clips (compás más marcado; no se
dibujan si quedarían a < 5 px). Diálogo "Rejilla…" (BPM 30–300 + división).
Limitación: la rejilla es del carril de audio (si se borran todos los clips
el carril desaparece y vuelve a "sin rejilla").

## 8. Silencio inicial de MP3/AAC (gapless)
Android informa `encoder-delay`/`encoder-padding` (KEY_ENCODER_DELAY/PADDING)
para .mp3/.mp4 pero `MediaCodec` NO los recorta; `MediaPlayer` sí. El
export decodifica con MediaCodec, así que salía desfasado ~25–50 ms respecto
del preview. `AudioProcessor.trimGapless` recorta delay y padding tras
decodificar; un metadato corrupto (que vaciaría el audio) se ignora.
Nota: `sourceDurationMs` de un clip ya importado no se recalcula.

## Tests nuevos (esta iteración)
`AudioReverseTest`, `AudioGaplessTrimTest`, `AudioTimeRangeOpsTest`
(+ `AudioGridSnapTest`).

## Pendiente / decisiones abiertas
- Las ediciones finas de audio (volumen, pan…) siguen sin undo por diseño
  original; las estructurales nuevas (rango) sí lo tienen.
- Preview: un solo `MediaPlayer` (clips solapados: suena uno).
- `ensureLoaded` con `prepare()` síncrono al cambiar de archivo.
- El export de audio ignora `speedKeyframes`/`freezeFrames` (preview igual).
- Sin compilar en esta sesión: ejecutar `./gradlew testDebugUnitTest
  assembleDebug` y probar en tablet los casos de la lista de QA del chat.
