package com.yeivikas.olyzecs.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yeivikas.olyzecs.engine.camera.ZoomXLevels
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// ============================================================================
// MÓDULO "ZOOM X" — ventana flotante. Archivo propio (ver el comentario
// grande de ZoomXWheel.kt para el porqué de la separación en archivos).
//
// Esta es la ÚNICA pieza del módulo que conoce el mecanismo real de
// commit del editor — pero solo a través de LAMBDAS desacopladas (mismo
// contrato exacto que ya usa JoystickLayerMover.kt para lo mismo), nunca
// recibiendo un `Layer`/`EditorViewModel` propios. Quien instancia esta
// ventana (EditorScreen.kt) pasa `getScale`/`setScale`/`getAlpha`/
// `setAlpha`/`commitFrame`/`layerIsEditable` apuntando a sus MISMAS
// variables locales `scale`/`alpha`/`commitLiveFrame()` que ya alimentan
// al joystick y al pellizco de dos dedos — un solo punto de verdad para
// "cuál es el zoom vigente de la capa", nunca un estado paralelo propio
// de este módulo.
// ============================================================================

private val ZOOM_X_FLOATING_WINDOW_DEFAULT_SIZE = DpSize(230.dp, 330.dp)

/**
 * Cuánto baja el `alpha` en el punto más bajo de la transición "gradual"
 * — no llega a 0 (invisible del todo se lee como un corte, no un
 * desvanecido) pero sí lo bastante bajo para que el salto de escala quede
 * disimulado detrás del efecto, en vez de sentirse como un "salto seco".
 */
private const val ZOOM_X_FADE_DIP_ALPHA = 0.12f
private const val ZOOM_X_FADE_OUT_MS = 140
private const val ZOOM_X_FADE_IN_MS = 200

/**
 * Ventana flotante de "Zoom X" — arrastrable y redimensionable, mismo
 * chrome ([FloatingToolWindow]) que Recolor/Color Básico/3D Básico.
 *
 * @param getScale/setScale el `scale` VIGENTE de la capa (mismo campo que
 *   ya mueve el pellizco de dos dedos y el joystick — ver `scale` en
 *   EditorScreen.kt). Esta ventana nunca inventa su propio valor de zoom
 *   paralelo: siempre lee/escribe este mismo campo.
 * @param getAlpha/setAlpha el `alpha` vigente de la capa — el vehículo de
 *   la "transición gradual" (ver [ZOOM_X_FADE_DIP_ALPHA]): ya existe como
 *   campo animable del motor de cámara (`CameraFrame.alpha`), así que la
 *   transición no necesita ningún mecanismo nuevo, solo anima este mismo
 *   campo con [androidx.compose.animation.core.animate] y lo persiste con
 *   [commitFrame] al terminar.
 * @param layerIsEditable si la capa actual admite cambios (misma
 *   condición que ya usa JoystickLayerMover — `!layer.locked`).
 * @param commitFrame persiste el estado actual — mismo `commitLiveFrame()`
 *   que ya usan el joystick y el pellizco (keyframe si Grabar está activo,
 *   pose estática si no). Ver el comentario grande sobre `triggerLevelChange`
 *   más abajo para CUÁNDO se llama exactamente durante la transición
 *   gradual (nunca en cada frame de la animación — solo al fijar el nuevo
 *   nivel y al terminar de reaparecer).
 * @param maxZoomExponent pasado tal cual a [ZoomXLevels.levels] — el motor
 *   de paradas es configurable (ver su comentario grande); esta ventana no
 *   decide el tope, solo lo reenvía.
 */
// `internal` (no `public`, el default) — MISMO motivo que
// [FloatingToolWindow]/[FloatingWindowMinimizedRegistry] son `internal`:
// esta función recibe `minimizedRegistry: FloatingWindowMinimizedRegistry`
// en su firma, y ese tipo es `internal`. Kotlin no permite que una
// función `public` exponga un tipo `internal` en su firma — no importa
// que ambos vivan en el mismo módulo, es un error de compilación
// (`Function 'public' exposes its 'internal' parameter type`), no una
// advertencia. Como este composable solo se instancia desde dentro de
// este mismo módulo (EditorScreen.kt), `internal` es exactamente lo que
// corresponde — nunca estuvo pensado para llamarse desde otro módulo.
@Composable
internal fun ZoomXFloatingWindow(
    getScale: () -> Float,
    setScale: (Float) -> Unit,
    getAlpha: () -> Float,
    setAlpha: (Float) -> Unit,
    layerIsEditable: () -> Boolean,
    commitFrame: () -> Unit,
    gradualFadeEnabled: Boolean,
    onGradualFadeEnabledChange: (Boolean) -> Unit,
    maxZoomExponent: Int = ZoomXLevels.DEFAULT_MAX_EXPONENT,
    initialOffset: Offset,
    onClose: () -> Unit,
    // Ver el comentario grande sobre `onInteracted` en [FloatingToolWindow]
    // — se reenvía tal cual, sin lógica propia acá, mismo criterio que
    // todas las demás ventanas flotantes de este editor.
    onInteracted: () -> Unit = {},
    minimizedRegistry: FloatingWindowMinimizedRegistry,
    containerSizePx: IntSize,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    // Un solo Job por ventana — un cambio de nivel nuevo mientras la
    // transición anterior seguía animando CANCELA a la anterior en vez de
    // dejarlas correr en paralelo peleándose por el mismo `alpha` (mismo
    // criterio de "el gesto más nuevo manda" que ya usa
    // [applyLivePreviewAndScheduleCommit] en ColorBasicoFloatingWindow
    // para su propio debounce).
    var fadeJob by remember { mutableStateOf<Job?>(null) }

    val levels = remember(maxZoomExponent) { ZoomXLevels.levels(maxZoomExponent) }

    /**
     * Dispara el cambio a [newLevel] — con o sin la transición gradual,
     * según [gradualFadeEnabled] en el momento en que SE INICIA el cambio
     * (si el usuario apaga el switch a mitad de una transición ya en
     * curso, esa transición en curso se completa tal como empezó; el
     * switch nuevo solo aplica al PRÓXIMO cambio de nivel — comportamiento
     * predecible, sin que una transición cambie de forma a mitad de
     * camino).
     *
     * SIN transición gradual: un solo escritura + un solo commit,
     * inmediato — el "clic" seco de un anillo de zoom real.
     *
     * CON transición gradual: `alpha` baja hasta [ZOOM_X_FADE_DIP_ALPHA]
     * (SOLO estado local — ver el comentario grande en `getAlpha`/
     * `setAlpha` de la firma: esto es exactamente lo mismo que ya hace el
     * joystick mientras se arrastra, antes de su propio commit
     * throttleado), RECIÉN AHÍ se fija `newLevel` en `scale` y se
     * commitea (el nuevo nivel de zoom queda persistido con el alpha
     * todavía bajo — invisible el "salto" de escala), y por último
     * `alpha` vuelve a 1 y se commitea una segunda vez (el estado final
     * en reposo siempre queda con alpha=1, nunca a mitad de camino).
     * Ningún commit ocurre EN CADA FRAME de la animación — eso saturaría
     * de keyframes densísimos cualquier grabación en curso; solo estos
     * dos puntos fijos, igual que el joystick throttlea sus propios
     * commits en vez de commitear cada frame de arrastre.
     *
     * NOTA HONESTA sobre modo Grabar: estos dos commits, con Grabar
     * activo, quedan como DOS keyframes reales en la pista — el motor de
     * cámara ya interpola alpha entre ellos con su easing de siempre
     * (EASE_IN_OUT, ver `commitLiveFrame`), así que la REPRODUCCIÓN sí
     * queda con un desvanecido suave, no un corte seco. Lo que NO está
     * garantizado es que la FORMA exacta de esa curva grabada coincida
     * con la curva de [ZOOM_X_FADE_OUT_MS]/[ZOOM_X_FADE_IN_MS] de acá
     * abajo — esa animación corre en tiempo real (reloj de pared, para
     * la vista previa en vivo mientras se interactúa) y es completamente
     * independiente de dónde caigan los dos keyframes en el eje de
     * tiempo del proyecto. Es la misma característica (no un bug nuevo
     * de este módulo) que ya tiene cualquier commit throttleado de este
     * editor — el joystick tampoco garantiza que la curva grabada
     * reproduzca exactamente la velocidad real con la que se arrastró.
     */
    fun triggerLevelChange(newLevel: Float) {
        if (!layerIsEditable()) return
        fadeJob?.cancel()
        if (!gradualFadeEnabled) {
            setScale(newLevel)
            commitFrame()
            return
        }
        fadeJob = coroutineScope.launch {
            animate(
                initialValue = getAlpha(),
                targetValue = ZOOM_X_FADE_DIP_ALPHA,
                animationSpec = tween(ZOOM_X_FADE_OUT_MS)
            ) { value, _ -> setAlpha(value) }

            setScale(newLevel)
            commitFrame()

            animate(
                initialValue = getAlpha(),
                targetValue = 1f,
                animationSpec = tween(ZOOM_X_FADE_IN_MS)
            ) { value, _ -> setAlpha(value) }
            commitFrame()
        }
    }

    val currentScale = getScale()
    val selectedIndex = remember(currentScale, levels) { ZoomXLevels.nearestIndex(currentScale, levels) }

    FloatingToolWindow(
        title = "Zoom X",
        titleIcon = { tint -> ZoomXModuleIcon(tint = tint, iconSize = 14.dp) },
        initialOffset = initialOffset,
        initialSize = ZOOM_X_FLOATING_WINDOW_DEFAULT_SIZE,
        // A diferencia de Recolor/Color Básico (que sí ofrecen "volver al
        // original" porque reemplazan el archivo de imagen), Zoom X no
        // tiene onDeleteEffect propio en esta primera versión — cerrar
        // deja el zoom aplicado tal como quedó, igual que cerrar (sin
        // arrastrar a "Eliminar") ya se comporta en las otras ventanas.
        onClose = onClose,
        onInteracted = onInteracted,
        containerSizePx = containerSizePx,
        minimizedRegistry = minimizedRegistry,
        modifier = modifier
    ) {
        ZoomXModulePanel(
            levels = levels,
            selectedIndex = selectedIndex,
            onLevelSelected = { index -> triggerLevelChange(levels[index]) },
            gradualFadeEnabled = gradualFadeEnabled,
            onGradualFadeEnabledChange = onGradualFadeEnabledChange,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}
