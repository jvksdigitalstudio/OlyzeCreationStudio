package com.yeivikas.olyzecs.engine.camera

/**
 * Una pista de cámara: lista ordenada de keyframes que definen cómo se
 * mueve el "encuadre virtual" sobre una capa a lo largo del tiempo.
 *
 * Esto es el equivalente directo a lo que hace After Effects con las
 * propiedades de posición/escala/rotación de una capa, pero simplificado
 * a lo que necesitamos para efectos tipo documental (pan, zoom, tilt, dolly).
 */
class CameraTrack(
    initialKeyframes: List<Keyframe> = emptyList(),
    // --- Pose ESTÁTICA de la capa, completamente separada de la lista de
    // keyframes de animación. Antes, mover una capa sin estar en modo
    // Grabar terminaba escribiendo un keyframe "disfrazado" en el
    // instante 0 para que la posición no se perdiera — pero eso seguía
    // siendo, técnicamente, un keyframe, y el usuario lo notó y no le
    // gustó ("no se ve profesional guardar un keyframe sin estar
    // grabando"). Con este campo aparte, mover una capa sin grabar NUNCA
    // toca [_keyframes] — solo actualiza [baseFrame], que ni siquiera
    // aparece en la pista de keyframes del timeline. Es el mismo criterio
    // que After Effects: las propiedades de Transform de una capa existen
    // SIEMPRE, independientemente de si tiene animación armada o no; los
    // keyframes son un agregado opcional encima de esa base.
    initialBaseFrame: CameraFrame = CameraFrame(0f, 0f, 1f, 0f, 1f)
) {

    // FASE 2 — auditoría de concurrencia (hallazgo confirmado): [frameAt]
    // corre en el hilo de GL en cada frame (llamado desde
    // GLRenderer.onDrawFrame ~60 veces por segundo) y ANTES leía directo
    // de `_keyframes` — la MISMA ArrayList mutable que [addOrReplace] /
    // [remove] / [replaceAll] modifican estructuralmente (`removeAll`,
    // `add`, `sortBy`, `clear`, `addAll`) desde el hilo principal cada vez
    // que el usuario arrastra un keyframe, deshace/rehace, o el editor
    // aplica un cambio. Dos hilos leyendo/escribiendo la misma ArrayList
    // sin ningún tipo de sincronización es una condición de carrera real:
    // en el peor caso, `frameAt` podía indexar `_keyframes[i]` justo
    // cuando `sortBy`/`clear` la estaban reordenando o vaciando a mitad de
    // camino, arriesgando un `IndexOutOfBoundsException` o, más sutil
    // todavía, leer una lista a medio ordenar (un frame de cámara
    // temporalmente incoherente — el "glitch" que describe el brief de
    // Fase 2, sección 1).
    //
    // Arreglo (publicación segura, sin locks): `_keyframes` sigue siendo
    // la lista de TRABAJO, mutada únicamente desde el hilo principal
    // (dueño exclusivo, sin cambios de ownership) — pero YA NO se expone
    // directamente. Cada método que la modifica termina publicando, en
    // `keyframesSnapshot`, una lista COMPLETAMENTE NUEVA e inmutable
    // (`.toList()` crea una copia real) marcada `@Volatile`. `keyframes`
    // (la propiedad pública, la única forma de leerlos desde afuera) lee
    // ese snapshot, nunca `_keyframes`. `@Volatile` garantiza la
    // visibilidad entre hilos de esa reasignación (sin él, el hilo de GL
    // podría seguir viendo indefinidamente una referencia vieja cacheada
    // en registro/caché de CPU, sin ninguna garantía de cuándo — o si —
    // llega a ver la nueva). Como el snapshot publicado NUNCA se muta una
    // vez creado (es una lista nueva cada vez, no la misma reutilizada),
    // cualquier hilo que tenga una referencia a él la puede recorrer con
    // total seguridad sin importar qué esté haciendo el hilo principal
    // con `_keyframes` al mismo tiempo — exactamente la garantía que pide
    // la Fase 2: "Crear snapshot → modificar keyframes → snapshot
    // permanece igual" (sección 18-B), acá satisfecha en el propio motor,
    // sin necesitar que quien llama recuerde copiar nada.
    private val _keyframes = initialKeyframes.sortedBy { it.timeMs }.toMutableList()

    @Volatile
    private var keyframesSnapshot: List<Keyframe> = _keyframes.toList()

    val keyframes: List<Keyframe> get() = keyframesSnapshot

    // Mismo razonamiento que arriba: `baseFrame` es un `CameraFrame`
    // inmutable (data class de solo `val`), así que el VALOR nunca puede
    // quedar a medio escribir — pero sin `@Volatile` seguía sin haber
    // garantía de VISIBILIDAD entre hilos (el hilo de GL podría no
    // enterarse nunca, o tardar arbitrariamente, de una reasignación
    // hecha desde el hilo principal en `updateBaseFrame`).
    @Volatile
    var baseFrame: CameraFrame = initialBaseFrame
        private set

    /** Actualiza la pose estática — nunca crea ni toca ningún [Keyframe]. */
    fun updateBaseFrame(frame: CameraFrame) {
        baseFrame = frame
    }

    fun addOrReplace(keyframe: Keyframe) {
        _keyframes.removeAll { it.timeMs == keyframe.timeMs }
        _keyframes.add(keyframe)
        _keyframes.sortBy { it.timeMs }
        publishKeyframesSnapshot()
    }

    fun remove(timeMs: Long) {
        _keyframes.removeAll { it.timeMs == timeMs }
        publishKeyframesSnapshot()
    }

    /**
     * Reemplaza TODOS los keyframes de golpe, preservando la identidad de
     * este objeto CameraTrack (y por lo tanto de la [Layer] que lo contiene).
     * Se usa al restaurar un snapshot de undo/redo: como [Layer.cameraTrack]
     * es un `val`, no se puede reasignar un CameraTrack nuevo sin perder la
     * textura GL ya subida — en cambio, se vacía y se rellena esta misma
     * instancia.
     */
    fun replaceAll(newKeyframes: List<Keyframe>) {
        _keyframes.clear()
        _keyframes.addAll(newKeyframes.sortedBy { it.timeMs })
        publishKeyframesSnapshot()
    }

    /** Publica una copia nueva e inmutable de [_keyframes] — ver el comentario grande de arriba. */
    private fun publishKeyframesSnapshot() {
        keyframesSnapshot = _keyframes.toList()
    }

    fun durationMs(): Long = keyframesSnapshot.maxOfOrNull { it.timeMs } ?: 0L

    /**
     * Devuelve el encuadre interpolado en [timeMs]. Si no hay keyframes,
     * devuelve [baseFrame] (la pose estática de la capa — no una animación,
     * el mismo valor para cualquier instante del proyecto).
     * Si hay uno solo, se mantiene fijo (estático) en ese valor.
     * Si el tiempo está antes del primero o después del último, se sostiene
     * el valor del extremo más cercano (comportamiento estándar de cine).
     */
    fun frameAt(timeMs: Long): CameraFrame {
        // Lee SIEMPRE el snapshot publicado (`keyframesSnapshot`), nunca
        // `_keyframes` directamente — este método corre en el hilo de GL,
        // que no es el dueño de `_keyframes` (ver comentario grande más
        // arriba). Se captura la referencia UNA sola vez al entrar: aunque
        // el hilo principal reemplace `keyframesSnapshot` por una lista
        // más nueva mientras este método sigue corriendo, `frames` acá
        // abajo sigue apuntando a la lista que ya tenía — inmutable,
        // consistente de punta a punta, nunca a medio construir. La
        // interpolación en sí vive en [CameraFrameInterpolation] (función
        // pura, compartida con `RenderLayerSnapshot.frameAt`) para no
        // duplicar la fórmula en dos archivos.
        return CameraFrameInterpolation.at(keyframesSnapshot, baseFrame, timeMs)
    }
}
