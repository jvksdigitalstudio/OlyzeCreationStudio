package com.yeivikas.olyzecs.engine.audio

import android.net.Uri
import com.yeivikas.olyzecs.engine.scene.Layer

/**
 * Clip de audio de fondo del proyecto, en memoria (análogo a [com.yeivikas.olyzecs.engine.scene.Layer]
 * pero a nivel de proyecto entero: solo puede haber uno). [sourceUri] apunta al
 * archivo ya copiado localmente por `ProjectStorage` una vez guardado, o al
 * Uri de SAF recién elegido antes del primer guardado.
 */
class AudioClip(
    var sourceUri: Uri,
    var displayName: String,
    /** Duración total del ARCHIVO de audio original (no del proyecto). */
    var sourceDurationMs: Long,
    /** 0f = silencio, 1f = volumen original, hasta 1.5f para dar algo de boost. */
    var volume: Float = 1f,
    var muted: Boolean = false,
    /** Punto del archivo original donde arranca a sonar (permite recortar el inicio). */
    var trimStartMs: Long = 0L,
    /** Si el audio es más corto que la duración del proyecto, lo repite en loop. */
    var loop: Boolean = true,
    var fadeInMs: Long = 400L,
    var fadeOutMs: Long = 600L,
    /**
     * Posición de esta pista dentro de la playlist de capas del timeline
     * (`TimelineView`), en el MISMO espacio de valores que [com.yeivikas.olyzecs.engine.scene.Layer.zIndex]
     * — `TimelineView.renderTracks` ordena TODO (capas + esta pista) en
     * forma DESCENDENTE por este valor: el más alto se lista primero
     * (arriba del todo) y se pinta más al frente. Es puramente de
     * ORDENAMIENTO VISUAL: a diferencia de `zIndex` en `Layer`, este campo
     * NO participa del renderizado GPU (el audio no se dibuja, no tiene
     * textura ni cámara) — el audio sigue siendo un dato de proyecto
     * aparte, nunca un `Layer`.
     *
     * Se fija UNA sola vez, al importar el audio por primera vez —
     * ver [nextAudioTrackOrder], la ÚNICA función que debe calcular este
     * valor para una pista nueva (usada por `EditorViewModel.importAudio`
     * y `AudioApiImpl.setAudioClip` — los dos únicos puntos de entrada
     * que crean un `AudioClip` nuevo). A PEDIDO EXPLÍCITO DEL USUARIO:
     * toda capa/pista nueva (audio, imagen, modelo, etc.) debe respetar
     * el orden real de carga y quedar DESPUÉS de la última que ya
     * existía, nunca delante — como acá "más alto" = "más arriba/al
     * frente", eso significa que el audio nuevo necesita el `trackOrder`
     * MÁS BAJO de todo el proyecto en ese momento (más bajo que el
     * `zIndex` mínimo entre todas las capas, fondo incluido), no el más
     * alto. (Antes de esta corrección, ambos puntos de entrada usaban un
     * valor que terminaba siendo siempre el MÁS ALTO del proyecto —
     * `layers.size` en un caso, `0f` fijo en el otro — así que el audio
     * aparecía siempre arriba de todo sin importar cuándo se cargó; bug
     * real, reportado con capturas de pantalla.)
     *
     * Reemplazar el archivo de audio (mismo clip, otro Uri) conserva este
     * valor: cambiar de archivo no debe reordenar la playlist.
     */
    var trackOrder: Float = 0f,
    /**
     * Punto del PROYECTO (no del archivo fuente — eso es [trimStartMs])
     * donde arranca a sonar este clip, en milisegundos desde el inicio
     * del timeline. Se fija arrastrando la pista de audio hacia la
     * derecha o la izquierda en `TimelineView`/`AudioTrackRow` — antes de
     * este campo, `AudioProcessor.buildProjectSamples` siempre mezclaba
     * el audio arrancando en t=0 del proyecto sin excepción; con este
     * campo, el mezclado real respeta el desplazamiento (ver Fase 3,
     * `AudioProcessor`).
     */
    var timelineStartMs: Long = 0L,
    // ============================================================
    // Paridad con Layer (candado de orden + color de identidad) — A
    // PEDIDO EXPLÍCITO DEL USUARIO: el panel de acciones "al costado" de
    // esta pista (ver AudioTrackRow.kt) usaba el MISMO set visual de 5
    // íconos que cualquier capa de imagen (lápiz/paleta/candado de
    // orden/candado/basura) pero con 4 de esos 5 como placeholders sin
    // ninguna función real conectada — "se ve activo pero no hace nada"
    // es justo el bug real reportado ("doy click en cualquier ícono y no
    // funciona"). Estos campos son la contraparte de
    // `Layer.orderLocked`/`Layer.customColorArgb`/
    // `Layer.customGradientStartArgb`/`Layer.customGradientEndArgb`/
    // `Layer.useGradientColor`/`Layer.gradientAngleDegrees`/
    // `Layer.gradientIsRadial`/`Layer.useBlackAndWhiteMode` — ahora la
    // pista de audio puede renombrarse, pintarse (sólido o degradado) y
    // bloquear su ORDEN exactamente igual que una capa, en vez de solo
    // poder eliminarse. Ver `EditorViewModel.renameAudio` /
    // `toggleAudioOrderLock` / `setAudioCustomColor` / `setAudioGradient`
    // / `resetAudioColor` para las mutaciones reales, y `AudioTrackRow`
    // para el consumo en UI.
    //
    // SIN el equivalente de `Layer.locked` (candado de "bloquear en el
    // canvas") — A PEDIDO EXPLÍCITO DEL USUARIO, corregido en una pasada
    // posterior: ese candado existe en `Layer` para bloquear el
    // movimiento/edición de la capa DENTRO DEL CANVAS, algo que
    // simplemente no aplica acá — el audio no se dibuja ni se manipula en
    // el canvas, así que un candado "de canvas" para esta pista no tiene
    // ningún efecto que bloquear. Llegó a existir brevemente en una
    // versión anterior (bloqueaba el arrastre horizontal dentro del
    // carril, a falta de un concepto de "canvas" propio del audio) pero
    // se quitó por completo de acá — modelo, persistencia, ViewModel y
    // UI — en vez de dejarlo enterrado sin ningún ícono que lo dispare:
    // un campo que nunca se puede tocar desde ningún lado es código
    // muerto, no una función real. `orderLocked` SÍ se queda: bloquear el
    // arrastre VERTICAL de reordenamiento en la playlist es un concepto
    // igual de válido para audio que para cualquier capa de imagen.
    var orderLocked: Boolean = false,
    // Color sólido elegido a mano — null = sin personalizar, la fila usa
    // el color de identidad de audio por defecto (ver AUDIO_TRACK_COLOR
    // en AudioTrackRow.kt, el único "color de fábrica" que tiene sentido
    // acá: a diferencia de una capa de imagen, un clip de audio no tiene
    // ningún píxel del que extraer un dominante).
    var customColorArgb: Int? = null,
    var customGradientStartArgb: Int? = null,
    var customGradientEndArgb: Int? = null,
    var useGradientColor: Boolean = false,
    var gradientAngleDegrees: Float = 90f,
    var gradientIsRadial: Boolean = false,
    var useBlackAndWhiteMode: Boolean = false
) {
    /**
     * Crea una copia con los campos indicados reemplazados. Se usa en el
     * ViewModel en vez de mutar esta instancia in-place: reemplazar la
     * REFERENCIA (no solo el contenido) es lo que garantiza, sin ninguna
     * ambigüedad, que Compose y el `equals()` de [EditorUiState] vean el
     * cambio como un estado genuinamente nuevo y disparen recomposición —
     * mutar un campo de una instancia ya existente puede quedar "invisible"
     * para código que compara por referencia.
     */
    fun copy(
        displayName: String = this.displayName,
        volume: Float = this.volume,
        muted: Boolean = this.muted,
        trimStartMs: Long = this.trimStartMs,
        loop: Boolean = this.loop,
        fadeInMs: Long = this.fadeInMs,
        fadeOutMs: Long = this.fadeOutMs,
        trackOrder: Float = this.trackOrder,
        timelineStartMs: Long = this.timelineStartMs,
        orderLocked: Boolean = this.orderLocked,
        customColorArgb: Int? = this.customColorArgb,
        customGradientStartArgb: Int? = this.customGradientStartArgb,
        customGradientEndArgb: Int? = this.customGradientEndArgb,
        useGradientColor: Boolean = this.useGradientColor,
        gradientAngleDegrees: Float = this.gradientAngleDegrees,
        gradientIsRadial: Boolean = this.gradientIsRadial,
        useBlackAndWhiteMode: Boolean = this.useBlackAndWhiteMode
    ): AudioClip = AudioClip(
        sourceUri = sourceUri,
        displayName = displayName,
        sourceDurationMs = sourceDurationMs,
        volume = volume,
        muted = muted,
        trimStartMs = trimStartMs,
        loop = loop,
        fadeInMs = fadeInMs,
        fadeOutMs = fadeOutMs,
        trackOrder = trackOrder,
        timelineStartMs = timelineStartMs,
        orderLocked = orderLocked,
        customColorArgb = customColorArgb,
        customGradientStartArgb = customGradientStartArgb,
        customGradientEndArgb = customGradientEndArgb,
        useGradientColor = useGradientColor,
        gradientAngleDegrees = gradientAngleDegrees,
        gradientIsRadial = gradientIsRadial,
        useBlackAndWhiteMode = useBlackAndWhiteMode
    )
}

/**
 * Calcula el [AudioClip.trackOrder] que debe recibir una pista de audio
 * NUEVA (primera importación, no reemplazo de archivo — ver KDoc de
 * [AudioClip.trackOrder]) para quedar ubicada DESPUÉS de la última capa
 * ya cargada en el proyecto, respetando el orden real de carga tal como
 * lo pidió el usuario — nunca delante de capas ya existentes.
 *
 * ÚNICA función que debe calcular este valor: los dos puntos de entrada
 * que crean un `AudioClip` nuevo (`EditorViewModel.importAudio`, flujo
 * manual desde la UI, y `AudioApiImpl.setAudioClip`, flujo programático
 * de la EliNer API) llaman acá en vez de repetir la cuenta cada uno por
 * su lado — así, si el criterio de posicionamiento cambia el día de
 * mañana, cambia en un solo lugar y los dos flujos quedan sincronizados
 * automáticamente, sin riesgo de que uno se corrija y el otro no (fue
 * exactamente lo que pasó antes de esta función: cada punto de entrada
 * tenía su propia cuenta, y las dos estaban mal de la misma forma).
 *
 * [existingLayers] es la lista de capas de imagen ya cargadas al momento
 * de importar el audio (`ActiveProjectReader.getLayers()` /
 * `EditorUiState.layers`, según el llamador). Como `TimelineView`
 * ordena la playlist en forma DESCENDENTE por `trackOrder`/`zIndex` (el
 * valor más alto se lista primero, arriba del todo — ver
 * `TimelineView.renderTracks`), "después de la última capa" significa
 * un valor MENOR que el `zIndex` mínimo entre todas las capas existentes
 * (fondo incluido, que puede tener `zIndex` negativo — ver
 * `EditorViewModel.addBackgroundLayer`), nunca uno mayor.
 */
fun nextAudioTrackOrder(existingLayers: List<Layer>): Float =
    ((existingLayers.minOfOrNull { it.zIndex } ?: 0) - 1).toFloat()

/**
 * Id "sentinela" que identifica la fila de audio dentro del arrastre-
 * reordenamiento VERTICAL de la playlist mezclada del timeline (capas +
 * audio — ver `TimelineView.renderTracks`/`reorderTrack`). No puede
 * colisionar con un id real de capa: los ids de [Layer] son
 * `UUID.randomUUID().toString()`, nunca esta cadena fija.
 */
const val AUDIO_TRACK_ID = "__audio_track__"
