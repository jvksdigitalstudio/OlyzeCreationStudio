package com.yeivikas.olyzecs.engine.color

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Motor ÚNICO de cálculo del degradado repartido entre varias filas
 * (capas y carriles de audio) del timeline. Lo usan por igual la UI y el
 * ViewModel: no existe ninguna otra fórmula de reparto en el proyecto.
 *
 * Contrato (mismo que "Color selected → Gradient" del Channel Rack de
 * FL Studio: las filas seleccionadas se colorean EN SECUENCIA entre el
 * color inicial y el final):
 *
 * - La posición de la fila `k` de `n` es `t = k / (n - 1)`. La PRIMERA
 *   fila recibe exactamente el color inicial y la ÚLTIMA exactamente el
 *   final. Con una sola fila, recibe el color inicial.
 * - El resultado es SIEMPRE un color opaco: el alfa de las entradas se
 *   ignora.
 * - Es una función pura de sus argumentos: sin estado, sin Android, sin
 *   Compose. Se prueba en JVM (ver `GroupGradientEngineTest`).
 *
 * Es también la ÚNICA fuente de las otras dos operaciones de color de fila
 * que antes vivían en la UI con fórmulas propias: el punto medio de un
 * degradado ([midpoint], el color plano de un degradado propio de una fila)
 * y el realce de acento de una fila suelta ([strongAccent]).
 *
 * Los colores se manejan como enteros ARGB de 32 bits (`0xAARRGGBB`),
 * igual que el resto del proyecto (`Layer.customColorArgb`).
 */
object GroupGradientEngine {

    private const val OPAQUE_ALPHA_MASK: Int = -0x1000000 // 0xFF000000

    /**
     * Posición `t` en `[0, 1]` de la fila [index] dentro de un reparto de
     * [count] filas. `0` para la primera, `1` para la última; con
     * [count] `<= 1` siempre `0`.
     */
    fun positionOf(index: Int, count: Int): Double {
        if (count <= 1) return 0.0
        return index.coerceIn(0, count - 1).toDouble() / (count - 1).toDouble()
    }

    /**
     * Reparte el degradado [startArgb] → [endArgb] entre [count] filas y
     * devuelve el color sólido opaco de cada una, en orden de playlist.
     */
    fun distribute(startArgb: Int, endArgb: Int, count: Int, space: GradientColorSpace): IntArray =
        IntArray(count.coerceAtLeast(0)) { index ->
            sample(startArgb, endArgb, positionOf(index, count), space)
        }

    /**
     * Color opaco en la posición [t] (acotada a `[0, 1]`) del degradado
     * [startArgb] → [endArgb], interpolado en [space]. Los extremos
     * (`t <= 0` y `t >= 1`) devuelven exactamente los colores de entrada.
     */
    fun sample(startArgb: Int, endArgb: Int, t: Double, space: GradientColorSpace): Int {
        val position = if (t.isNaN()) 0.0 else t.coerceIn(0.0, 1.0)
        if (position <= 0.0) return opaque(startArgb)
        if (position >= 1.0) return opaque(endArgb)
        return when (space) {
            GradientColorSpace.HUE -> sampleHue(startArgb, endArgb, position)
            GradientColorSpace.RGB -> sampleRgb(startArgb, endArgb, position)
            GradientColorSpace.PERCEPTUAL -> samplePerceptual(startArgb, endArgb, position)
        }
    }

    /**
     * Color plano que representa a un degradado de dos extremos donde solo
     * cabe UN color (borde de diálogo, acento): su punto medio en Oklab, el
     * mismo espacio del degradado repartido multicapa. Opaco, como todo el
     * motor; quien necesite conservar la opacidad la promedia aparte.
     */
    fun midpoint(startArgb: Int, endArgb: Int): Int =
        sample(startArgb, endArgb, 0.5, GradientColorSpace.PERCEPTUAL)

    /**
     * Realce de acento de una fila SUELTA (borde, miniatura, panel): mismo
     * matiz con la saturación al máximo y el brillo un 5 % más alto. Opaco.
     *
     * Un color SIN matiz (blanco, gris, negro: saturación 0) se conserva sin
     * matiz. Subirle la saturación a un gris —como se hacía antes— lo
     * convertía en rojo, porque el matiz de un gris no está definido y vale 0:
     * con el modo Negro y Blanco la fila se veía roja en vez de gris.
     */
    fun strongAccent(argb: Int): Int {
        val hsv = toHsv(argb)
        val saturation = if (hsv.saturation == 0.0) 0.0 else 1.0
        return fromHsv(hsv.hue, saturation, (hsv.value * 1.05).coerceIn(0.0, 1.0))
    }

    // ------------------------------------------------------------------
    // Canales ARGB
    // ------------------------------------------------------------------

    private fun red(argb: Int): Int = (argb shr 16) and 0xFF
    private fun green(argb: Int): Int = (argb shr 8) and 0xFF
    private fun blue(argb: Int): Int = argb and 0xFF

    private fun opaque(argb: Int): Int = argb or OPAQUE_ALPHA_MASK

    private fun pack(red: Int, green: Int, blue: Int): Int =
        OPAQUE_ALPHA_MASK or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)

    private fun lerp(from: Double, to: Double, t: Double): Double = from + (to - from) * t

    // ------------------------------------------------------------------
    // RGB: mezcla directa canal a canal
    // ------------------------------------------------------------------

    private fun sampleRgb(startArgb: Int, endArgb: Int, t: Double): Int = pack(
        lerp(red(startArgb).toDouble(), red(endArgb).toDouble(), t).roundToInt(),
        lerp(green(startArgb).toDouble(), green(endArgb).toDouble(), t).roundToInt(),
        lerp(blue(startArgb).toDouble(), blue(endArgb).toDouble(), t).roundToInt()
    )

    // ------------------------------------------------------------------
    // HUE: ruta corta del matiz; saturación y brillo lineales (HSV)
    // ------------------------------------------------------------------

    /** Matiz en grados `[0, 360)`, saturación y brillo en `[0, 1]`. */
    private class Hsv(val hue: Double, val saturation: Double, val value: Double) {
        /** Sin color propio: el matiz no está definido y no debe arrastrar la ruta. */
        val isAchromatic: Boolean get() = saturation == 0.0 || value == 0.0
    }

    private fun toHsv(argb: Int): Hsv {
        val r = red(argb) / 255.0
        val g = green(argb) / 255.0
        val b = blue(argb) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val sector = when {
            delta == 0.0 -> 0.0
            max == r -> ((g - b) / delta) % 6.0
            max == g -> (b - r) / delta + 2.0
            else -> (r - g) / delta + 4.0
        }
        val hue = (sector * 60.0).let { if (it < 0.0) it + 360.0 else it }
        val saturation = if (max == 0.0) 0.0 else delta / max
        return Hsv(hue, saturation, max)
    }

    private fun fromHsv(hue: Double, saturation: Double, value: Double): Int {
        val wrapped = ((hue % 360.0) + 360.0) % 360.0
        val sector = wrapped / 60.0
        val chroma = value * saturation
        val x = chroma * (1.0 - abs(sector % 2.0 - 1.0))
        val offset = value - chroma
        val (r, g, b) = when (sector.toInt()) {
            0 -> Triple(chroma, x, 0.0)
            1 -> Triple(x, chroma, 0.0)
            2 -> Triple(0.0, chroma, x)
            3 -> Triple(0.0, x, chroma)
            4 -> Triple(x, 0.0, chroma)
            else -> Triple(chroma, 0.0, x)
        }
        return pack(
            ((r + offset) * 255.0).roundToInt(),
            ((g + offset) * 255.0).roundToInt(),
            ((b + offset) * 255.0).roundToInt()
        )
    }

    private fun sampleHue(startArgb: Int, endArgb: Int, t: Double): Int {
        val from = toHsv(startArgb)
        val to = toHsv(endArgb)
        // Un extremo sin color (blanco, gris, negro) toma el matiz del otro:
        // así el degradado no hace un rodeo por un matiz que no existe.
        val fromHue = if (from.isAchromatic && !to.isAchromatic) to.hue else from.hue
        val toHue = if (to.isAchromatic && !from.isAchromatic) from.hue else to.hue
        // Diferencia con signo por la ruta más corta, en (-180, 180]. Con
        // exactamente 180° se elige siempre el sentido creciente (resultado
        // determinista).
        var delta = ((toHue - fromHue + 540.0) % 360.0) - 180.0
        if (delta <= -180.0) delta += 360.0
        return fromHsv(
            hue = fromHue + delta * t,
            saturation = lerp(from.saturation, to.saturation, t),
            value = lerp(from.value, to.value, t)
        )
    }

    // ------------------------------------------------------------------
    // PERCEPTUAL: Oklab (Björn Ottosson), sobre sRGB lineal
    // ------------------------------------------------------------------

    private fun srgbToLinear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun linearToSrgb(linear: Double): Int {
        val clamped = linear.coerceIn(0.0, 1.0)
        val c = if (clamped <= 0.0031308) clamped * 12.92 else 1.055 * clamped.pow(1.0 / 2.4) - 0.055
        return (c * 255.0).roundToInt()
    }

    private class Oklab(val lightness: Double, val a: Double, val b: Double)

    private fun toOklab(argb: Int): Oklab {
        val r = srgbToLinear(red(argb))
        val g = srgbToLinear(green(argb))
        val b = srgbToLinear(blue(argb))
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return Oklab(
            lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            b = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    private fun fromOklab(lab: Oklab): Int {
        val l = (lab.lightness + 0.3963377774 * lab.a + 0.2158037573 * lab.b).let { it * it * it }
        val m = (lab.lightness - 0.1055613458 * lab.a - 0.0638541728 * lab.b).let { it * it * it }
        val s = (lab.lightness - 0.0894841775 * lab.a - 1.2914855480 * lab.b).let { it * it * it }
        return pack(
            linearToSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
            linearToSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
            linearToSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)
        )
    }

    private fun samplePerceptual(startArgb: Int, endArgb: Int, t: Double): Int {
        val from = toOklab(startArgb)
        val to = toOklab(endArgb)
        return fromOklab(
            Oklab(
                lightness = lerp(from.lightness, to.lightness, t),
                a = lerp(from.a, to.a, t),
                b = lerp(from.b, to.b, t)
            )
        )
    }
}
