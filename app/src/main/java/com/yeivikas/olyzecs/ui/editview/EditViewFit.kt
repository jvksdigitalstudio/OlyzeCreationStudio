package com.yeivikas.olyzecs.ui.editview

import com.yeivikas.olyzecs.engine.render.PerspectiveCameraMath
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Cuánto hay que escalar la capa para que, en la vista de edición, ocupe el visor con un margen — sin que se
 * salga y sin "exagerar".
 *
 * ## Qué calcula
 * `LayerDrawer.drawLayer` ya encaja (contain) la imagen en el visor cuando `scale = 1`: el lado que más limita
 * toca el borde del visor. Después aplica `scale`, el estiramiento (`scaleX`/`scaleY`) y la rotación, EN ESE
 * ORDEN y en el espacio normalizado del visor (no en píxeles). Esta función invierte ese cálculo: devuelve el
 * `scale` con el que el rectángulo resultante (ya rotado) mide como mucho [fraction] del ancho y del alto del
 * visor. Una imagen apaisada ocupa [fraction] del ANCHO; una vertical o cuadrada queda limitada por el ALTO.
 *
 * ## Profundidad (parallax)
 * `drawLayer` también multiplica el tamaño por la compensación de profundidad de la capa
 * ([PerspectiveCameraMath.depthCompensationFor]): una capa de fondo (`parallaxFactor < 1`, p. ej. el fondo
 * importado, 0,35) se dibuja MÁS GRANDE que una de sujeto con la misma `scale`. Si el ajuste la ignorara, esa
 * capa se saldría del visor (con 0,35 llegaría a ~132 % en vez de 90 %). Por eso [parallaxFactor] es un
 * parámetro obligatorio y se usa la misma función que el renderer y el overlay de selección: una sola fuente de
 * verdad, imposible de desincronizar.
 *
 * Es matemática pura (sin Android ni Compose), probada en JVM; `EditorScreen` solo la invoca.
 */
object EditViewFit {

    /** Parte del visor que ocupa la imagen: 90 % deja un margen visible alrededor sin desperdiciar pantalla. */
    const val FILL_FRACTION = 0.90f

    private const val EPSILON = 1e-6f

    /**
     * Compensación de profundidad que `drawLayer` aplicará a una capa con [parallaxFactor]. Un valor no finito
     * (dato corrupto) se trata como capa de sujeto (1,0), igual que hace la carga del proyecto.
     */
    private fun depthCompensation(parallaxFactor: Float): Float =
        PerspectiveCameraMath.depthCompensationFor(if (parallaxFactor.isFinite()) parallaxFactor else 1f)

    /**
     * Escala provisional al entrar al modo edición, cuando el visor aún no se midió: "contain" al
     * [FILL_FRACTION] del visor, corregida por profundidad. El valor exacto (con estiramiento y rotación) lo
     * aplica después [fitScale].
     */
    fun provisionalScale(parallaxFactor: Float): Float = FILL_FRACTION / depthCompensation(parallaxFactor)

    /**
     * @param parallaxFactor `parallaxFactor` de la capa que se edita (ver "Profundidad" arriba).
     * @return la escala de cámara (`frame.scale`) que ajusta la capa, o `null` si todavía no se puede calcular
     *   (alguna medida es 0: la capa aún no tiene bitmap o el visor aún no se midió). Quien llama debe volver a
     *   intentarlo cuando cambien las medidas.
     */
    fun fitScale(
        imageWidthPx: Int,
        imageHeightPx: Int,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        scaleX: Float,
        scaleY: Float,
        rotationDeg: Float,
        parallaxFactor: Float,
        fraction: Float = FILL_FRACTION
    ): Float? {
        if (imageWidthPx <= 0 || imageHeightPx <= 0 || viewportWidthPx <= 0 || viewportHeightPx <= 0) return null

        val imageAspect = imageWidthPx.toFloat() / imageHeightPx
        val viewportAspect = viewportWidthPx.toFloat() / viewportHeightPx

        // Fracción del visor que ocupa la imagen con scale = 1 (el encaje "contain" de LayerDrawer).
        val baseWidth: Float
        val baseHeight: Float
        if (imageAspect > viewportAspect) {
            baseWidth = 1f
            baseHeight = viewportAspect / imageAspect
        } else {
            baseHeight = 1f
            baseWidth = imageAspect / viewportAspect
        }

        // Tamaño que tendría con scale = 1 INCLUYENDO la compensación de profundidad de la capa.
        val depth = depthCompensation(parallaxFactor)
        val width = baseWidth * abs(scaleX) * depth
        val height = baseHeight * abs(scaleY) * depth

        // Rectángulo envolvente tras rotar (en el mismo espacio normalizado en que rota la GPU).
        val radians = Math.toRadians(rotationDeg.toDouble())
        val cosine = abs(cos(radians)).toFloat()
        val sine = abs(sin(radians)).toFloat()
        val boundsWidth = width * cosine + height * sine
        val boundsHeight = width * sine + height * cosine

        val largest = max(boundsWidth, boundsHeight)
        if (largest < EPSILON) return null
        return fraction / largest
    }
}
