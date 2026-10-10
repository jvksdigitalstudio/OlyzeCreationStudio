package com.yeivikas.olyzecs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

// --- CORREGIDO (bug real, no solo de organización): `translateX`/
// `translateY` de una capa NO están en píxeles — son unidades normalizadas
// cuyo rango típico es -panLimit..panLimit (panLimit = 2f * zoom; ver el
// paneo táctil del preview en EditorScreen.kt, que mueve la capa en
// fracciones del ancho/alto del canvas, no en px). Antes esta velocidad
// estaba expresada en píxeles reales de pantalla (dp convertidos con la
// densidad del dispositivo) y se sumaba directo sobre esa unidad
// normalizada: con el stick a fondo, un solo frame (16ms) ya sumaba
// decenas de "unidades" sobre un rango total de apenas 4 — la capa salía
// disparada del canvas casi al instante, sin importar qué tan despacio se
// empujara el stick (la velocidad real siempre era órdenes de magnitud
// mayor al rango válido). Ahora está expresada en la MISMA unidad
// normalizada que `translateX/Y`: con panLimit = 2f (zoom 1x, el caso
// normal), este valor significa que el stick a fondo tarda
// aproximadamente ese tiempo (en segundos) en llevar la capa de centro
// hasta el borde del rango de paneo.
private const val DEFAULT_JOYSTICK_SPEED_UNITS_PER_SEC = 1.4f

// Mismo throttle de 120 ms que ya usan el arrastre táctil del preview y
// los sliders de cámara (ver EditorScreen.kt) — sin esto, mantener el
// stick empujado en modo Grabar escribiría un keyframe nuevo en cada frame
// (hasta 60+ por segundo) en vez de un puñado por segundo.
private const val DEFAULT_COMMIT_THROTTLE_MS = 120L

/**
 * Traduce el vector normalizado (-1..1 por eje) que reporta
 * [GtaStyleJoystick] en movimiento continuo real sobre la capa
 * seleccionada — frame a frame, mientras el stick se mantenga empujado.
 *
 * Vive en su propio archivo, separado de EditorScreen.kt y de
 * Joystick.kt, a propósito: es lógica de FÍSICA/MOVIMIENTO pura. No sabe
 * dibujar nada, no sabe qué es un `Layer` ni un `CameraFrame`, y no toca
 * ninguna variable de EditorScreen directamente — todo lo que necesita
 * leer o escribir se lo pasan como lambdas al construirla (inyección de
 * dependencias simple). Eso permite:
 *
 *  - Leer esta clase de punta a punta sin tener que abrir EditorScreen.kt.
 *  - Escribir un test unitario común (sin Compose, sin Android) que
 *    empuje `setDirection(...)`, llame `onFrame(...)` a mano con un
 *    `dtSeconds` fijo, y verifique que `translateX/Y` cambiaron lo
 *    esperado — algo imposible de hacer hoy con la lógica mezclada
 *    adentro del composable gigante de EditorScreen.
 *  - Reusar esta misma pieza el día de mañana si algún otro control (un
 *    D-pad, un gamepad físico, lo que sea) necesita mover una capa de la
 *    misma manera: solo necesita llamar `setDirection(x, y)`.
 *
 * @param getTranslateX / setTranslateX acceso a la coordenada X de la capa
 *   activa (unidad normalizada -panLimit..panLimit, ver comentario de
 *   [DEFAULT_JOYSTICK_SPEED_UNITS_PER_SEC] arriba).
 * @param getTranslateY / setTranslateY lo mismo para Y. El signo se
 *   invierte internamente al aplicar el movimiento (empujar el stick hacia
 *   abajo mueve la capa igual que arrastrarla hacia abajo con el dedo —
 *   misma convención que ya usa el paneo táctil del preview).
 * @param panLimit límite de paneo VIGENTE en este instante (cambia con el
 *   zoom de la capa) — se consulta en cada frame, nunca se cachea, para
 *   que un cambio de zoom en pleno movimiento de joystick se respete de
 *   inmediato.
 * @param layerIsEditable si ahora mismo hay una capa seleccionada Y esa
 *   capa no está bloqueada. Mientras sea `false`, el joystick puede seguir
 *   moviéndose (la UI en sí no se congela) pero no mueve nada.
 * @param commitFrame confirma la posición actual como keyframe (equivalente
 *   a `commitLiveFrame()` en EditorScreen.kt) — se llama con el mismo
 *   throttle que el resto de los gestos de esta pantalla, y siempre una
 *   última vez al soltar el stick, para que la posición final nunca quede
 *   "flotando" sin guardar.
 */
class JoystickLayerMover(
    private val getTranslateX: () -> Float,
    private val setTranslateX: (Float) -> Unit,
    private val getTranslateY: () -> Float,
    private val setTranslateY: (Float) -> Unit,
    private val panLimit: () -> Float,
    private val layerIsEditable: () -> Boolean,
    private val commitFrame: () -> Unit,
    private val speedUnitsPerSec: Float = DEFAULT_JOYSTICK_SPEED_UNITS_PER_SEC,
    private val commitThrottleMs: Long = DEFAULT_COMMIT_THROTTLE_MS
) {
    private var directionX = 0f
    private var directionY = 0f
    private var lastCommitAtMs = 0L
    private var pendingCommit = false

    /** Llamado desde [GtaStyleJoystick.onDirectionChange] cada vez que el stick se mueve. */
    fun setDirection(x: Float, y: Float) {
        directionX = x
        directionY = y
    }

    /**
     * Se llama una vez por frame de pantalla (ver [rememberJoystickLayerMover]
     * más abajo, que arma el loop real con `withFrameNanos`). [dtSeconds] es
     * el tiempo real transcurrido desde el frame anterior — así la
     * velocidad es la misma sin importar el fps del dispositivo.
     *
     * [nowMs] DEBE venir de una fuente de tiempo MONOTÓNICA (la misma que
     * produce [dtSeconds] — ver [rememberJoystickLayerMover], que deriva
     * ambos de `frameNanos`), nunca de `System.currentTimeMillis()`: ese es
     * el reloj de pared del dispositivo, que puede saltar (ajuste manual,
     * sincronización NTP, cambio de zona horaria) y rompería el throttle de
     * abajo — ver el comentario grande en [rememberJoystickLayerMover] para
     * el bug real que esto causaba.
     */
    fun onFrame(dtSeconds: Float, nowMs: Long) {
        if (directionX == 0f && directionY == 0f) {
            if (pendingCommit) {
                // El stick volvió al centro (se soltó) con un commit
                // todavía sin descargar por el throttle de abajo — la
                // posición final SIEMPRE se guarda, nunca queda a mitad de
                // camino del último throttle.
                commitFrame()
                pendingCommit = false
            }
            return
        }
        if (!layerIsEditable() || dtSeconds <= 0f) return

        val limit = panLimit()
        setTranslateX((getTranslateX() + directionX * speedUnitsPerSec * dtSeconds).coerceIn(-limit, limit))
        setTranslateY((getTranslateY() - directionY * speedUnitsPerSec * dtSeconds).coerceIn(-limit, limit))
        pendingCommit = true

        if (nowMs - lastCommitAtMs >= commitThrottleMs) {
            commitFrame()
            lastCommitAtMs = nowMs
            pendingCommit = false
        }
    }
}

/**
 * Arma un [JoystickLayerMover] y el loop por-frame que lo alimenta
 * (`withFrameNanos`, igual que cualquier animación continua en Compose).
 * El loop vive todo el ciclo de vida del composable que llama a esta
 * función (nunca se reinicia solo), pero solo hace trabajo real cuando el
 * stick está fuera del centro — el resto del tiempo el costo es
 * despreciable (un par de chequeos por frame).
 *
 * Este es EL ÚNICO punto de contacto entre Compose/EditorScreen y
 * [JoystickLayerMover]: arma la clase, la conecta al reloj de frames, y
 * devuelve el objeto ya listo para que EditorScreen solo necesite llamar
 * `mover.setDirection(x, y)` desde el callback del joystick.
 */
@Composable
fun rememberJoystickLayerMover(
    getTranslateX: () -> Float,
    setTranslateX: (Float) -> Unit,
    getTranslateY: () -> Float,
    setTranslateY: (Float) -> Unit,
    panLimit: () -> Float,
    layerIsEditable: () -> Boolean,
    commitFrame: () -> Unit
): JoystickLayerMover {
    val mover = remember {
        JoystickLayerMover(
            getTranslateX = getTranslateX,
            setTranslateX = setTranslateX,
            getTranslateY = getTranslateY,
            setTranslateY = setTranslateY,
            panLimit = panLimit,
            layerIsEditable = layerIsEditable,
            commitFrame = commitFrame
        )
    }
    LaunchedEffect(mover) {
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                val dtSeconds = if (lastFrameNanos == 0L) 0f
                    else ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.1f)
                lastFrameNanos = frameNanos
                // BUG (encontrado y corregido acá) — reloj mezclado: `dtSeconds`
                // se calcula con `frameNanos`, un reloj MONOTÓNICO (equivale a
                // `System.nanoTime()`, nunca retrocede, inmune a que el usuario
                // o el sistema cambien la hora del dispositivo). Antes, acá
                // abajo se pasaba `System.currentTimeMillis()` — el reloj de
                // PARED — como `nowMs` para el throttle de commits de
                // [JoystickLayerMover.onFrame]. Si ese reloj de pared salta
                // (ajuste manual, sincronización NTP, cambio de zona horaria)
                // mientras el stick está sostenido, `nowMs - lastCommitAtMs`
                // puede quedar negativo o saltar de golpe, y el throttle deja
                // de escribir keyframes en el ritmo real del movimiento —
                // huecos o ráfagas que no tienen nada que ver con lo que hizo
                // el dedo. `dtSeconds`, en cambio, seguía siendo correcto
                // todo el tiempo, porque usa la fuente monotónica — la
                // inconsistencia estaba en mezclar dos relojes distintos
                // para dos cosas que tienen que ir sincronizadas. Ahora
                // `nowMs` sale del MISMO `frameNanos` monotónico (convertido
                // a ms), así física (`dtSeconds`) y programación de commits
                // (`nowMs`) comparten una única fuente de tiempo de punta a
                // punta — el estándar en cualquier motor de juego serio.
                mover.onFrame(dtSeconds, frameNanos / 1_000_000L)
            }
        }
    }
    return mover
}
