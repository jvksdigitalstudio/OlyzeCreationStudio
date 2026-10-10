# Auditoría completa del proyecto — hallazgos y correcciones reales

> Este documento registra una pasada de auditoría solicitada sobre TODO
> el código real del proyecto (no una fase numerada específica),
> buscando bugs, fallos o implementaciones incorrectas, con corrección
> quirúrgica de lo que se encontró.
>
> **Disclosure de alcance, sin adornos:** el proyecto tiene ~53.000
> líneas de Kotlin. Esta pasada auditó en profundidad los módulos de
> mayor riesgo real (exportación de video/audio, decodificación,
> compositor GPU, extracción de color) más un barrido automatizado
> (grep) buscando patrones de bug conocidos (fugas de recursos,
> excepciones silenciadas, aserciones `!!` riesgosas, división por
> cero) en el resto del árbol. **No es una revisión línea por línea de
> las 53.000 líneas** — eso no es realista en una sesión sin poder
> compilar ni ejecutar el proyecto. Se declara explícitamente qué se
> revisó a fondo y qué solo se barrió con patrones automatizados.

## Bugs reales encontrados y corregidos

### 1. `AudioProcessor.applyVolumeAndFades` — el fade-out podía no tener NINGÚN efecto audible

**Archivo:** `engine/audio/AudioProcessor.kt`

**Severidad:** Media — afecta a un caso de uso real y alcanzable desde
la UI (audio de fondo más corto que el proyecto, sin loop, con fade-out
configurado), sin necesitar ninguna acción "anormal" del usuario.

**El bug:** el fade-out se calculaba siempre relativo a `totalFrames`
(la duración COMPLETA del proyecto). Si el clip de audio era más corto
que el proyecto y no estaba en loop, el resto del buffer quedaba
relleno de silencio (ceros). El punto de inicio del fade-out
(`totalFrames - fadeOutFrames`) podía caer DENTRO de esa zona de
silencio — es decir, el fundido se aplicaba sobre algo que ya era
silencio (0 × cualquier ganancia = 0, sin cambio perceptible), mientras
que el audio REAL terminaba de golpe, sin ningún fundido. Resultado:
el usuario configura "fade out: 1 segundo" y el audio corta seco de
todas formas — la opción no tenía efecto.

**La corrección:** el fade-out (y, por consistencia, el tope del
fade-in) ahora se calcula relativo al último frame con audio REAL
(`audibleFrames`, el `written` real que ya calculaba
`buildProjectSamples`), no relativo al final del proyecto completo. Si
el clip llena todo el proyecto o está en loop, `audibleFrames ==
totalFrames` y el comportamiento es idéntico al de antes — cero
regresión para el caso común.

**Cambio de visibilidad necesario para poder testear:** `applyVolumeAndFades`
y `buildProjectSamples` (y la clase `DecodedPcm`) pasaron de `private`
a `internal`, y `applyVolumeAndFades` pasó a recibir
`volume`/`muted`/`fadeInMs`/`fadeOutMs` como parámetros primitivos en
vez de un `AudioClip` completo — así el test puede ejercitar la lógica
real sin necesitar construir un `AudioClip` (que exige un
`android.net.Uri` real, no disponible en un unit test JVM sin
Robolectric). `buildProjectSamples` sigue siendo el único llamador real
en producción y le sigue pasando exactamente los mismos valores.

**Test agregado:** `engine/audio/AudioProcessorFadeTest.kt` — 5 tests,
JVM puro: fade-out sí llega al audio real cuando el clip es corto,
sigue funcionando igual que antes cuando el clip llena el proyecto,
`muted`/`volume=0` siguen silenciando todo el buffer.

### 2. `VideoExporter` — orden de liberación de recursos EGL/Codec al revés

**Archivo:** `engine/export/VideoExporter.kt`

**Severidad:** Media-baja — comportamiento indefinido dependiente del
fabricante de GPU, no reproducible en todos los dispositivos, pero un
patrón de "usar después de liberar" real a nivel de driver gráfico.

**El bug:** en el bloque `finally` de limpieza, el orden era: liberar
`MediaMuxer` → liberar `MediaCodec` → recién después liberar
EGL (`eglMakeCurrent`/`eglDestroySurface`/`eglDestroyContext`). El
`eglSurface` envuelve el `Surface` que expone el propio `codec`
(`codec.createInputSurface()`) — liberar el codec ANTES de destruir el
`eglSurface` que lo envuelve dejaba a EGL destruyendo una superficie
cuyo productor (BufferQueue) ya había sido liberado por
`codec.release()`. Esto contradice el orden documentado por Android y
usado en las muestras de referencia (Grafika/bigflake
`CodecInputSurface`): el lado EGL debe liberarse siempre ANTES que el
codec/`Surface` que envuelve.

**La corrección:** se invirtió el orden — `releaseEgl()` ahora corre
antes de `codec.stop()/release()`. Sin cambio de comportamiento en el
caso normal (ambos ya estaban completamente drenados en ese punto de
la limpieza), solo se corrige la SECUENCIA.

## Áreas auditadas a fondo y confirmadas SIN bugs reales

- **`AudioProcessor.decodeToPcm`/`decodeWavManually`/`probeDurationMs`**:
  manejo de `MediaExtractor`/`MediaCodec`/`MediaMetadataRetriever` con
  `try/finally` correcto en todos los caminos, incluidos los `return`
  tempranos dentro del `try` (Kotlin ejecuta el `finally` igual).
- **`ColorExtraction.dominantColor`/`extractPalette`/`recolor`**: lógica
  de baldes de matiz/blend por distancia revisada matemáticamente
  (normalización de pesos, clamps de canal) — consistente, sin
  división por cero posible (todos los `weight`/`totalWeight` se
  chequean `<= 0` antes de dividir).
- **`LayerDrawer.uploadTexture`/`deleteTexture`**: ya tenía un
  `glFinish()` documentado como corrección de un bug real anterior
  (textura corrupta por reciclar el bitmap antes de que el driver
  terminara la copia asíncrona) — confirmado que sigue intacto.
- **`GLRenderer` — poda de texturas huérfanas**: la resta de sets
  (`layerTextures.keys - liveLayersById.keys`) es correcta; no deja
  texturas sin liberar ni borra las de capas todavía vivas.
- **4 usos de `!!` en todo el proyecto** (`EditorScreen.kt` ×2,
  `ProjectsScreen.kt`, `AudioProcessor.kt`): los 4 están genuinamente
  protegidos por una condición previa que garantiza no-nulidad en la
  práctica (revisados uno por uno) — no son bugs, aunque
  `ProjectsScreen.kt:1170` depende de un ID de preset hardcodeado que
  coincida con el catálogo (acoplamiento frágil pero intencional, de
  bajo riesgo real en un proyecto de este tamaño).
- **Concurrencia `Layer` mutable durante export**: se evaluó si
  `VideoExporter` leyendo campos `@Volatile` de `Layer` (`visible`/
  `lookSettings`/`cameraTrack`) mientras el usuario sigue editando
  podría causar una composición inconsistente en el video exportado.
  Confirmado que `ExportDialog` es un `Dialog` de Compose modal que
  bloquea la interacción con el resto de la pantalla durante TODO el
  export (`state.exportProgress != null`) — el escenario no es
  alcanzable por la UI normal. Se documenta como debilidad de diseño
  latente (no como bug activo): si en el futuro se permitiera seguir
  editando durante el export, esta lectura directa de objetos mutables
  sin copia defensiva sí sería una carrera real.

## Barrido automatizado (grep) sin hallazgos adicionales

- Streams (`openInputStream`/`openOutputStream`) sin cerrar: 0 casos
  reales (todos los candidatos que el patrón encontró ya usaban `.use{}`,
  solo en una línea distinta a la de apertura).
- Bloques `catch` vacíos: 1 caso (`AppLogger.clear()`, borrado de
  archivo de log best-effort — aceptable, no crítico).
- Marcadores `TODO`/`FIXME` de trabajo pendiente sin resolver: 0.

## Deuda técnica ya documentada en fases anteriores, no reabierta acá

- PCM completo en memoria durante el export de audio (`ShortArray`
  completo, sin streaming) — deuda ya señalada en la Fase 4.2 original,
  no bloqueante, no se tocó en esta auditoría.
- `ProjectStorage.projectMutexes` sin límite de crecimiento — ya
  documentado, mismo argumento de riesgo/beneficio que antes.

## Nota metodológica

Cada corrección de este documento se verificó por: (1) lectura
completa del código real antes de tocar nada, (2) razonamiento
explícito sobre por qué es un bug y no una decisión de diseño
intencional, (3) el cambio mínimo que corrige el defecto sin alterar
comportamiento en el caso común, y (4) un test de regresión donde fue
técnicamente posible en JVM puro. Ningún cambio de este documento se
compiló ni se ejecutó en este entorno — el usuario debe validarlo vía
GitHub Actions, como el resto del proyecto.
